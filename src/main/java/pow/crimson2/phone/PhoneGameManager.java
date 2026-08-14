package pow.crimson2.phone;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class PhoneGameManager {
    private static final List<String> WORDS = List.of("blood", "night", "fangs", "stake", "crypt", "ghost", "torch", "raven");
    private final PhoneManager manager;
    private final Random random = new Random();
    private final Map<String, Blackjack> blackjack = new HashMap<>();
    private final Map<String, HigherLower> higherLower = new HashMap<>();
    private final Map<String, MemoryGame> memory = new HashMap<>();
    private final Map<String, Wordle> wordle = new HashMap<>();

    PhoneGameManager(PhoneManager manager) { this.manager = manager; }

    private String key(Player player) { return player.getUniqueId() + "|" + manager.displayName(player.getUniqueId().toString()).toLowerCase().replaceAll("[^a-z0-9_]", "_"); }
    private PhoneDataStore.GameRecord record(Player player) {
        PhoneDataStore.GameRecord record = manager.store().database().games.computeIfAbsent(key(player), ignored -> new PhoneDataStore.GameRecord());
        record.name = manager.displayName(player.getUniqueId().toString()); return record;
    }

    public void open(Player player) {
        PhoneDataStore.GameRecord record = record(player); manager.store().save();
        List<ActionButton> buttons = new ArrayList<>();
        if (record.chips > 0) {
            buttons.add(manager.button("Blackjack", "Dealer stands on 17; blackjack pays 3:2", () -> openBlackjackBet(player)));
            buttons.add(manager.button("Higher or Lower", "Build a streak and cash out", () -> openHigherLowerBet(player)));
            buttons.add(manager.button("Memory Sequence", "10-chip entry; 5 chips per cleared round", () -> startMemory(player)));
            buttons.add(manager.button("Wordle", "Guess the five-letter word", () -> startWordle(player)));
            buttons.add(manager.button("Connect 4", "Challenge a contact", () -> openPvpPicker(player, "c4")));
            buttons.add(manager.button("Tic-tac-toe", "Challenge a contact", () -> openPvpPicker(player, "ttt")));
            buttons.add(manager.button("Rock Paper Scissors", "Challenge a contact", () -> openPvpPicker(player, "rps")));
        } else buttons.add(manager.button("Out of chips — take 100", "One-time balance top-up when empty", () -> { record.chips = 100; manager.store().save(); open(player); }));
        buttons.add(manager.button("Your Matches", "Invites and active matches", () -> openMatches(player)));
        buttons.add(manager.button("Leaderboard", "Best chip balances", () -> openLeaderboard(player)));
        buttons.add(manager.button("PvP Records", "Wins, losses, and streaks", () -> openPvpRecords(player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        PhoneUi.showActions(player, "Games", "Chips: " + record.chips + " • Best: " + record.bestChips
                + "\nMemory best: " + record.memoryBestRound + " • Wordle best: " + record.wordleBestStreak, buttons, 2);
    }

    private void openBlackjackBet(Player player) {
        PhoneDataStore.GameRecord record = record(player);
        if (record.chips <= 0) { open(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        for (int bet : List.of(5, 10, 25, 50)) if (bet <= record.chips)
            buttons.add(manager.button("Bet " + bet, "Start hand", () -> dealBlackjack(player, bet)));
        buttons.add(manager.button("All in (" + record.chips + ")", "Bet all chips", () -> dealBlackjack(player, record.chips)));
        buttons.add(manager.button("Back", "Return to games", () -> open(player)));
        PhoneUi.showActions(player, "Blackjack", "Chips: " + record.chips + "\nBlackjack pays 3:2. Dealer stands on 17.", buttons, 2);
    }

    private void dealBlackjack(Player player, int bet) {
        Blackjack game = new Blackjack(bet); game.player.add(draw(game)); game.player.add(draw(game));
        game.dealer.add(draw(game)); game.dealer.add(draw(game)); blackjack.put(key(player), game);
        if (value(game.player) == 21) finishBlackjack(player, true); else openBlackjackHand(player);
    }

    private int draw(Blackjack game) { int card; do card = random.nextInt(52); while (game.player.contains(card) || game.dealer.contains(card)); return card; }
    private int value(List<Integer> cards) {
        int total = 0, aces = 0; for (int card : cards) { int rank = card % 13 + 1; if (rank == 1) { total += 11; aces++; } else total += Math.min(rank, 10); }
        while (total > 21 && aces-- > 0) total -= 10; return total;
    }
    private String hand(List<Integer> cards) {
        String[] suits = {"♠", "♥", "♦", "♣"}; StringBuilder out = new StringBuilder();
        for (int card : cards) { int rank = card % 13 + 1; String face = rank == 1 ? "A" : rank == 11 ? "J" : rank == 12 ? "Q" : rank == 13 ? "K" : String.valueOf(rank); out.append(face).append(suits[card / 13]).append(' '); }
        return out.toString();
    }
    private void openBlackjackHand(Player player) {
        Blackjack game = blackjack.get(key(player)); if (game == null) { open(player); return; }
        PhoneUi.showActions(player, "Blackjack", "Dealer: " + hand(List.of(game.dealer.get(0))) + " [??]\nYou: " + hand(game.player)
                + " (" + value(game.player) + ")\nBet: " + game.bet, List.of(
                manager.button("Hit", "Draw a card", () -> { game.player.add(draw(game)); if (value(game.player) > 21) finishBlackjack(player, false); else openBlackjackHand(player); }),
                manager.button("Stand", "End your turn", () -> finishBlackjack(player, false))), 2);
    }
    private void finishBlackjack(Player player, boolean natural) {
        Blackjack game = blackjack.remove(key(player)); if (game == null) return;
        PhoneDataStore.GameRecord record = record(player); int pv = value(game.player), dv = value(game.dealer), delta;
        if (pv > 21) delta = -game.bet;
        else { while (dv < 17) { game.dealer.add(draw(game)); dv = value(game.dealer); }
            delta = natural && dv != 21 ? (int)Math.floor(game.bet * 1.5) : dv > 21 || pv > dv ? game.bet : dv > pv ? -game.bet : 0; }
        record.chips = Math.max(0, record.chips + delta); record.bestChips = Math.max(record.bestChips, record.chips); manager.store().save();
        String result = delta > 0 ? "Won " + delta : delta < 0 ? "Lost " + -delta : "Push";
        PhoneUi.showActions(player, "Blackjack Result", "Dealer: " + hand(game.dealer) + " (" + dv + ")\nYou: " + hand(game.player) + " (" + pv + ")\n"
                + result + " • Chips: " + record.chips, List.of(manager.button("Play Again", "Place another bet", () -> openBlackjackBet(player)), manager.button("Games", "Return", () -> open(player))), 2);
    }

    private void openHigherLowerBet(Player player) {
        PhoneDataStore.GameRecord record = record(player); List<ActionButton> buttons = new ArrayList<>();
        for (int bet : List.of(5,10,25,50)) if (bet <= record.chips) buttons.add(manager.button("Bet " + bet, "Start", () -> startHigherLower(player, bet)));
        buttons.add(manager.button("Back", "Return", () -> open(player))); PhoneUi.showActions(player, "Higher or Lower", "Choose a stake. Equal ranks lose.", buttons, 2);
    }
    private void startHigherLower(Player player, int bet) { PhoneDataStore.GameRecord r=record(player); r.chips-=bet; higherLower.put(key(player), new HigherLower(bet, random.nextInt(13)+1)); manager.store().save(); openHigherLower(player); }
    private void openHigherLower(Player player) {
        HigherLower game=higherLower.get(key(player)); if(game==null){open(player);return;}
        PhoneUi.showActions(player,"Higher or Lower","Current rank: "+game.card+" • Streak: "+game.streak+" • Cash out: "+hlPayout(game),List.of(
                manager.button("Higher","Next rank is higher",()->guessHigherLower(player,true)), manager.button("Lower","Next rank is lower",()->guessHigherLower(player,false)),
                manager.button("Cash Out","Take current payout",()->cashHigherLower(player))),2);
    }
    private int hlPayout(HigherLower g){return g.bet + g.bet*g.streak/2;}
    private void guessHigherLower(Player player,boolean higher){HigherLower g=higherLower.get(key(player));int next=random.nextInt(13)+1;boolean win=higher?next>g.card:next<g.card;if(!win){higherLower.remove(key(player));manager.store().save();PhoneUi.showActions(player,"Higher or Lower","Next rank was "+next+". You lost the stake.",List.of(manager.button("Games","Return",()->open(player))),1);return;}g.card=next;g.streak++;openHigherLower(player);}
    private void cashHigherLower(Player player){HigherLower g=higherLower.remove(key(player));PhoneDataStore.GameRecord r=record(player);int payout=hlPayout(g);r.chips+=payout;r.bestChips=Math.max(r.bestChips,r.chips);manager.store().save();PhoneUi.showActions(player,"Higher or Lower","Cashed out "+payout+" chips on a "+g.streak+" streak.",List.of(manager.button("Games","Return",()->open(player))),1);}

    private void startMemory(Player player) {
        PhoneDataStore.GameRecord r=record(player); if(r.chips<10){player.sendMessage("§cYou need 10 chips.");return;} r.chips-=10;
        MemoryGame game=new MemoryGame(); memory.put(key(player),game); nextMemoryRound(player);
    }
    private void nextMemoryRound(Player player){MemoryGame g=memory.get(key(player));g.sequence.add(random.nextInt(4)+1);g.entered.clear();manager.store().save();
        PhoneUi.showActions(player,"Memory Sequence","Memorize: "+g.sequence+"\nClick Ready when prepared.",List.of(manager.button("Ready","Hide sequence",()->openMemoryInput(player)),manager.button("Give Up","Bank cleared-round payout",()->endMemory(player,"You gave up."))),2);}
    private void openMemoryInput(Player player){MemoryGame g=memory.get(key(player));List<ActionButton>b=new ArrayList<>();for(int i=1;i<=4;i++){int n=i;b.add(manager.button(String.valueOf(i),"Sequence button",()->memoryPress(player,n)));}b.add(manager.button("Give Up","Bank cleared-round payout",()->endMemory(player,"You gave up.")));PhoneUi.showActions(player,"Memory Sequence","Enter item "+(g.entered.size()+1)+" of "+g.sequence.size(),b,4);}
    private void memoryPress(Player player,int n){MemoryGame g=memory.get(key(player));int index=g.entered.size();if(g.sequence.get(index)!=n){endMemory(player,"Wrong symbol. Pattern was "+g.sequence);return;}g.entered.add(n);if(g.entered.size()==g.sequence.size())nextMemoryRound(player);else openMemoryInput(player);}
    private void endMemory(Player player,String reason){MemoryGame g=memory.remove(key(player));if(g==null)return;int cleared=Math.max(0,g.sequence.size()-1);PhoneDataStore.GameRecord r=record(player);r.memoryBestRound=Math.max(r.memoryBestRound,cleared);int pay=cleared*5;r.chips+=pay;r.bestChips=Math.max(r.bestChips,r.chips);manager.store().save();PhoneUi.showActions(player,"Memory Result",reason+"\nCleared "+cleared+" rounds for "+pay+" chips.",List.of(manager.button("Games","Return",()->open(player))),1);}

    private void startWordle(Player player){wordle.put(key(player),new Wordle(WORDS.get(random.nextInt(WORDS.size()))));openWordle(player);}
    private void openWordle(Player player){Wordle g=wordle.get(key(player));String history=String.join("\n",g.history);PhoneUi.showForm(player,"Wordle",history.isBlank()?"Six guesses. Green=correct; yellow=present; gray=absent.":history,List.of(DialogInput.text("guess",Component.text("Five-letter guess")).maxLength(5).build()),"Guess",view->guessWordle(player,view.getText("guess")),()->giveUpWordle(player));}
    private void giveUpWordle(Player player){Wordle g=wordle.remove(key(player));if(g==null){open(player);return;}PhoneDataStore.GameRecord r=record(player);r.wordleStreak=0;manager.store().save();PhoneUi.showActions(player,"Wordle","The word was "+g.word.toUpperCase()+".",List.of(manager.button("Games","Return",()->open(player))),1);}
    private void guessWordle(Player player,String guess){Wordle g=wordle.get(key(player));if(guess==null||guess.length()!=5){player.sendMessage("§cEnter five letters.");openWordle(player);return;}guess=guess.toLowerCase();StringBuilder result=new StringBuilder();for(int i=0;i<5;i++){char c=guess.charAt(i);result.append(c==g.word.charAt(i)?"🟩":g.word.indexOf(c)>=0?"🟨":"⬛");}g.history.add(result+" "+guess.toUpperCase());if(guess.equals(g.word)){wordle.remove(key(player));PhoneDataStore.GameRecord r=record(player);r.wordleStreak++;r.wordleBestStreak=Math.max(r.wordleBestStreak,r.wordleStreak);int pay=15+(r.wordleStreak-1)*10+(g.history.size()<=3?5:0);r.chips+=pay;r.bestChips=Math.max(r.bestChips,r.chips);manager.store().save();PhoneUi.showActions(player,"Wordle","Solved in "+g.history.size()+" guesses. Won "+pay+" chips.",List.of(manager.button("Games","Return",()->open(player))),1);return;}if(g.history.size()>=6){wordle.remove(key(player));PhoneDataStore.GameRecord r=record(player);r.wordleStreak=0;manager.store().save();PhoneUi.showActions(player,"Wordle","The word was "+g.word.toUpperCase()+".",List.of(manager.button("Games","Return",()->open(player))),1);return;}openWordle(player);}

    private void openLeaderboard(Player player){StringBuilder body=new StringBuilder();manager.store().database().games.values().stream().sorted(Comparator.comparingInt((PhoneDataStore.GameRecord r)->r.bestChips).reversed()).limit(10).forEach(r->body.append(r.name).append(" — ").append(r.bestChips).append('\n'));PhoneUi.showActions(player,"Chip Leaderboard",body.length()==0?"Nobody has played yet.":body.toString(),List.of(manager.button("Back","Return",()->open(player))),1);}
    private void openPvpRecords(Player player){StringBuilder body=new StringBuilder();manager.store().database().games.values().stream().sorted(Comparator.comparingInt((PhoneDataStore.GameRecord r)->r.wins).reversed()).limit(10).forEach(r->body.append(r.name).append(" — ").append(r.wins).append('/').append(r.losses).append(" best ").append(r.bestStreak).append('\n'));PhoneUi.showActions(player,"PvP Records",body.length()==0?"No matches played yet.":body.toString(),List.of(manager.button("Back","Return",()->open(player))),1);}

    private String gameName(String game) { return game.equals("c4") ? "Connect 4" : game.equals("ttt") ? "Tic-tac-toe" : "Rock Paper Scissors"; }

    private void openPvpPicker(Player player, String game) {
        List<ActionButton> buttons = new ArrayList<>(); String self = key(player);
        manager.store().player(self).contacts.keySet().stream().map(uuid -> Map.entry(uuid, org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(uuid))))
                .filter(entry -> entry.getValue() != null).forEach(entry -> buttons.add(manager.button(manager.displayName(entry.getKey()), "Choose wager", () -> openWager(player, game, entry.getKey()))));
        buttons.add(manager.button("Back", "Return to games", () -> open(player)));
        PhoneUi.showActions(player, gameName(game), buttons.size() == 1 ? "None of your contacts are online." : "Challenge an online contact.", buttons, 2);
    }

    private void openWager(Player player, String game, String opponent) {
        PhoneDataStore.GameRecord record = record(player); List<ActionButton> buttons = new ArrayList<>();
        buttons.add(manager.button("No wager", "Play for the record", () -> createMatch(player, game, opponent, 0)));
        for (int wager : List.of(5, 10, 25, 50)) if (wager <= record.chips)
            buttons.add(manager.button("Stake " + wager + " each", "Winner takes " + wager * 2, () -> createMatch(player, game, opponent, wager)));
        buttons.add(manager.button("Back", "Choose another contact", () -> openPvpPicker(player, game)));
        PhoneUi.showActions(player, "Wager", gameName(game) + " vs " + manager.displayName(opponent) + "\nYour chips: " + record.chips, buttons, 2);
    }

    private void createMatch(Player player, String game, String opponent, int wager) {
        Player target = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(opponent)); if (target == null) { player.sendMessage("§cThey went offline."); return; }
        String self = key(player), opponentKey = key(target); PhoneDataStore.GameRecord mine = record(player), theirs = record(target);
        if (self.equals(opponentKey) || mine.chips < wager || theirs.chips < wager) { player.sendMessage("§cBoth players must be able to cover the stake."); return; }
        PhoneDataStore.GameMatch match = new PhoneDataStore.GameMatch(); match.id = manager.store().database().nextMatchId++;
        match.game = game; match.challenger = self; match.opponent = opponentKey; match.challengerUuid = player.getUniqueId().toString(); match.opponentUuid = opponent; match.wager = wager;
        mine.chips -= wager; manager.store().database().matches.put(match.id, match); manager.store().save();
        target.sendMessage("§b[Phone] §f" + record(player).name + " challenged you to §e" + gameName(game) + "§f. Open Games → Your Matches.");
        openMatches(player);
    }

    private void openMatches(Player player) {
        String self = key(player); List<ActionButton> buttons = new ArrayList<>();
        manager.store().database().matches.values().stream().filter(match -> match.challenger.equals(self) || match.opponent.equals(self)).forEach(match -> {
            String other = match.challenger.equals(self) ? match.opponent : match.challenger;
            String status = match.state.equals("pending") ? (match.challenger.equals(self) ? "sent" : "INVITE") : isTurn(match, self) ? "YOUR TURN" : "waiting";
            buttons.add(manager.button(gameName(match.game) + " vs " + matchName(other), status + (match.wager > 0 ? " • " + match.wager + " chips" : ""), () -> openMatch(player, match.id)));
        });
        buttons.add(manager.button("Back", "Return to games", () -> open(player)));
        PhoneUi.showActions(player, "Your Matches", buttons.size() == 1 ? "No matches." : "Persistent turn-based matches", buttons, 2);
    }

    private void openMatch(Player player, int id) {
        PhoneDataStore.GameMatch match = manager.store().database().matches.get(id); if (match == null) { openMatches(player); return; }
        if (match.state.equals("pending")) { openPending(player, match); return; }
        if (match.game.equals("c4")) openConnect4(player, match); else if (match.game.equals("ttt")) openTtt(player, match); else openRps(player, match);
    }

    private void openPending(Player player, PhoneDataStore.GameMatch match) {
        boolean challenger = match.challenger.equals(key(player)); List<ActionButton> buttons = new ArrayList<>();
        if (challenger) buttons.add(manager.button("Withdraw", "Refund your stake", () -> cancelPending(player, match, false)));
        else { buttons.add(manager.button("Accept", "Match starts and your stake enters escrow", () -> acceptMatch(player, match)));
            buttons.add(manager.button("Decline", "Reject and refund challenger", () -> cancelPending(player, match, true))); }
        buttons.add(manager.button("Back", "Return to matches", () -> openMatches(player)));
        PhoneUi.showActions(player, gameName(match.game), challenger ? "Waiting for acceptance." : "Challenge from " + matchName(match.challenger)
                + (match.wager > 0 ? "\nStake: " + match.wager + " each" : ""), buttons, 2);
    }

    private void acceptMatch(Player player, PhoneDataStore.GameMatch match) {
        if (!match.opponent.equals(key(player))) return; PhoneDataStore.GameRecord record = record(player);
        if (record.chips < match.wager) { player.sendMessage("§cYou can no longer cover the stake."); return; }
        record.chips -= match.wager; match.state = "active"; match.turn = "challenger";
        int cells = match.game.equals("c4") ? 42 : match.game.equals("ttt") ? 9 : 0;
        for (int i = 0; i < cells; i++) match.board.add(""); manager.store().save(); notify(match.challengerUuid, "Challenge accepted — your turn in " + gameName(match.game)); openMatch(player, match.id);
    }

    private void cancelPending(Player player, PhoneDataStore.GameMatch match, boolean decline) {
        manager.store().database().games.get(match.challenger).chips += match.wager; manager.store().database().matches.remove(match.id); manager.store().save();
        notify(decline ? match.challengerUuid : match.opponentUuid, (decline ? "Challenge declined: " : "Challenge withdrawn: ") + gameName(match.game)); openMatches(player);
    }

    private boolean isTurn(PhoneDataStore.GameMatch match, String uuid) { return match.turn.equals("challenger") ? match.challenger.equals(uuid) : match.opponent.equals(uuid); }
    private String side(PhoneDataStore.GameMatch match, String uuid) { return match.challenger.equals(uuid) ? "challenger" : "opponent"; }
    private String other(PhoneDataStore.GameMatch match, String uuid) { return match.challenger.equals(uuid) ? match.opponent : match.challenger; }
    private String uuidFor(PhoneDataStore.GameMatch match, String characterKey) { return match.challenger.equals(characterKey) ? match.challengerUuid : match.opponentUuid; }
    private String matchName(String characterKey) { PhoneDataStore.GameRecord game = manager.store().database().games.get(characterKey); return game == null ? "Unknown" : game.name; }
    private void swapTurn(PhoneDataStore.GameMatch match) { match.turn = match.turn.equals("challenger") ? "opponent" : "challenger"; }
    private void notify(String uuid, String text) { Player player = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(uuid)); if (player != null && !manager.store().player(uuid).doNotDisturb) player.sendMessage("§b[Phone] §f" + text); }

    private void openConnect4(Player player, PhoneDataStore.GameMatch match) {
        StringBuilder board = new StringBuilder(); for (int row = 0; row < 6; row++) { for (int col = 0; col < 7; col++) { String cell=match.board.get(row*7+col); board.append(cell.isEmpty()?"⚪":cell.equals("challenger")?"🔴":"🟡"); } board.append('\n'); }
        List<ActionButton> buttons = new ArrayList<>(); if (isTurn(match,key(player))) for(int col=0;col<7;col++){int c=col;buttons.add(manager.button("Column "+(col+1),"Drop piece",()->dropConnect4(player,match,c)));}
        buttons.add(manager.button("Resign","Opponent wins",()->resign(player,match))); buttons.add(manager.button("Back","Return",()->openMatches(player)));
        PhoneUi.showActions(player,"Connect 4",board+(isTurn(match,key(player))?"Your turn":"Waiting for "+matchName(other(match,key(player)))),buttons,7);
    }

    private void dropConnect4(Player player, PhoneDataStore.GameMatch match, int col) {
        String self=key(player); if(!isTurn(match,self))return; int placed=-1; for(int row=5;row>=0;row--){int i=row*7+col;if(match.board.get(i).isEmpty()){match.board.set(i,side(match,self));placed=i;break;}}
        if(placed<0){player.sendMessage("§cThat column is full.");return;} String who=side(match,self);
        if(c4Win(match,who)){finishMatch(match,who,matchName(self)+" won Connect 4");openMatches(player);return;}
        if(match.board.stream().noneMatch(String::isEmpty)){finishMatch(match,"draw","Connect 4 ended level");openMatches(player);return;}
        swapTurn(match);manager.store().save();notify(uuidFor(match,other(match,self)),matchName(self)+" moved — your turn in Connect 4");openConnect4(player,match);
    }
    private boolean c4Win(PhoneDataStore.GameMatch m,String who){for(int r=0;r<6;r++)for(int c=0;c<7;c++)for(int[]d:new int[][]{{0,1},{1,0},{1,1},{1,-1}}){int n=0;for(int k=0;k<4;k++){int rr=r+d[0]*k,cc=c+d[1]*k;if(rr>=0&&rr<6&&cc>=0&&cc<7&&m.board.get(rr*7+cc).equals(who))n++;}if(n==4)return true;}return false;}

    private void openTtt(Player player, PhoneDataStore.GameMatch match){List<ActionButton>b=new ArrayList<>();for(int i=0;i<9;i++){int cell=i;String v=match.board.get(i);b.add(manager.button(v.isEmpty()?"·":v.equals("challenger")?"X":"O",v.isEmpty()?"Place mark":"Occupied",()->placeTtt(player,match,cell)));}b.add(manager.button("Resign","Opponent wins",()->resign(player,match)));b.add(manager.button("Back","Return",()->openMatches(player)));PhoneUi.showActions(player,"Tic-tac-toe",isTurn(match,key(player))?"Your turn":"Waiting for opponent",b,3);}
    private void placeTtt(Player player,PhoneDataStore.GameMatch m,int cell){String self=key(player);if(!isTurn(m,self)||!m.board.get(cell).isEmpty())return;String who=side(m,self);m.board.set(cell,who);if(tttWin(m,who)){finishMatch(m,who,matchName(self)+" won Tic-tac-toe");openMatches(player);return;}if(m.board.stream().noneMatch(String::isEmpty)){finishMatch(m,"draw","Tic-tac-toe ended level");openMatches(player);return;}swapTurn(m);manager.store().save();notify(uuidFor(m,other(m,self)),"Your turn in Tic-tac-toe");openTtt(player,m);}
    private boolean tttWin(PhoneDataStore.GameMatch m,String who){int[][]lines={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};for(int[]l:lines)if(m.board.get(l[0]).equals(who)&&m.board.get(l[1]).equals(who)&&m.board.get(l[2]).equals(who))return true;return false;}

    private void openRps(Player player,PhoneDataStore.GameMatch m){String side=side(m,key(player));String pick=side.equals("challenger")?m.challengerPick:m.opponentPick;List<ActionButton>b=new ArrayList<>();if(pick==null){for(String choice:List.of("rock","paper","scissors"))b.add(manager.button(choice.substring(0,1).toUpperCase()+choice.substring(1),"Lock choice",()->pickRps(player,m,choice)));}b.add(manager.button("Resign","Opponent wins",()->resign(player,m)));b.add(manager.button("Back","Return",()->openMatches(player)));String body="First to 2 • Score: "+m.challengerScore+" - "+m.opponentScore+(m.note==null?"":"\n"+m.note)+"\n"+(pick==null?"Choose — picks remain hidden.":"Choice locked. Waiting for opponent.");PhoneUi.showActions(player,"Rock Paper Scissors",body,b,3);}
    private void pickRps(Player player,PhoneDataStore.GameMatch m,String choice){String side=side(m,key(player));if(side.equals("challenger")){if(m.challengerPick!=null)return;m.challengerPick=choice;}else{if(m.opponentPick!=null)return;m.opponentPick=choice;}if(m.challengerPick!=null&&m.opponentPick!=null){String a=m.challengerPick,b=m.opponentPick,winner=a.equals(b)?"draw":(a.equals("rock")&&b.equals("scissors")||a.equals("paper")&&b.equals("rock")||a.equals("scissors")&&b.equals("paper"))?"challenger":"opponent";if(winner.equals("challenger"))m.challengerScore++;else if(winner.equals("opponent"))m.opponentScore++;m.note="Last round: "+a+" vs "+b;m.challengerPick=null;m.opponentPick=null;if(m.challengerScore>=2||m.opponentScore>=2){String winningKey=m.challengerScore>=2?m.challenger:m.opponent;finishMatch(m,m.challengerScore>=2?"challenger":"opponent",matchName(winningKey)+" won Rock Paper Scissors");openMatches(player);return;}manager.store().save();notify(m.challengerUuid,"RPS round complete — next pick ready");notify(m.opponentUuid,"RPS round complete — next pick ready");openRps(player,m);return;}manager.store().save();notify(uuidFor(m,other(m,key(player))),"Your pick is needed in Rock Paper Scissors");openRps(player,m);}

    private void resign(Player player,PhoneDataStore.GameMatch m){String winner=side(m,key(player)).equals("challenger")?"opponent":"challenger";finishMatch(m,winner,matchName(key(player))+" resigned");openMatches(player);}
    private void finishMatch(PhoneDataStore.GameMatch m,String winner,String reason){PhoneDataStore.GameRecord a=manager.store().database().games.get(m.challenger),b=manager.store().database().games.get(m.opponent);if(winner.equals("draw")){a.chips+=m.wager;b.chips+=m.wager;}else{String winUuid=winner.equals("challenger")?m.challengerUuid:m.opponentUuid;PhoneDataStore.GameRecord win=winner.equals("challenger")?a:b,lose=winner.equals("challenger")?b:a;win.chips+=m.wager*2;win.bestChips=Math.max(win.bestChips,win.chips);win.wins++;lose.losses++;win.streak++;lose.streak=0;win.bestStreak=Math.max(win.bestStreak,win.streak);notify(winUuid,"You won " + gameName(m.game) + ": " + reason);}notify(m.challengerUuid,reason);notify(m.opponentUuid,reason);manager.store().database().matches.remove(m.id);manager.store().save();}

    private static final class Blackjack { final int bet; final List<Integer> player=new ArrayList<>(),dealer=new ArrayList<>(); Blackjack(int bet){this.bet=bet;} }
    private static final class HigherLower { final int bet; int card,streak; HigherLower(int bet,int card){this.bet=bet;this.card=card;} }
    private static final class MemoryGame { final List<Integer> sequence=new ArrayList<>(),entered=new ArrayList<>(); }
    private static final class Wordle { final String word; final List<String> history=new ArrayList<>(); Wordle(String word){this.word=word;} }
}
