# /loop Skill Reference

Repeats a prompt or slash command on a fixed interval or self-paced cadence.

---

## Syntax

```
/loop [interval] <prompt>
/loop <prompt> every <N><unit>
/loop <prompt>               ← dynamic / self-paced mode
```

---

## Interval Parsing (in priority order)

1. **Leading token** — if the first word matches `\d+[smhd]` (e.g. `5m`, `2h`), that's the interval; the rest is the prompt.
2. **Trailing "every" clause** — if the input ends with `every <N><unit>` or `every <N> <unit-word>`, that's the interval. Only applies when what follows "every" is a time expression (`check every PR` has no interval).
3. **No interval** → **dynamic mode** (model self-paces each iteration).

If the resulting prompt is empty, usage is shown and nothing is scheduled.

---

## Fixed-Interval Mode (rules 1 & 2)

Interval is converted to a 5-field cron expression:

| Pattern | Cron | Notes |
|---|---|---|
| `Nm` (N ≤ 59) | `*/N * * * *` | every N minutes |
| `Nm` (N ≥ 60) | `0 */H * * *` | rounded to hours |
| `Nh` (N ≤ 23) | `0 */N * * *` | every N hours |
| `Nd` | `0 0 */N * *` | every N days at midnight |
| `Ns` | ceil to nearest minute | cron minimum is 1 minute |

If the interval doesn't divide cleanly (e.g. `7m`, `90m`), it's rounded to the nearest clean interval.

**Steps:**
1. `CronCreate` is called with the cron expression, the prompt, and `recurring: true`.
2. Confirmation is shown: job ID, cron expression, human-readable cadence, 7-day auto-expiry, and how to cancel with `CronDelete <job-id>`.
3. The prompt is executed **immediately** — doesn't wait for the first cron fire.

> Recurring jobs auto-expire after **7 days**.

---

## Dynamic Mode (rule 3 — no interval)

The model decides when the next iteration is worth running.

1. **Run the prompt now.**
2. If the next run is gated on an event (CI finish, file change, PR comment), arm a `Monitor` with `persistent: true`. Monitor events arrive as `<task-notification>` messages and wake the loop immediately.
3. Briefly confirm: self-pacing, whether a Monitor is the primary wake signal, what fallback delay was chosen.
4. Call `ScheduleWakeup` as the **last action** of the turn:
   - **With a Monitor:** use 1200–1800s as a fallback heartbeat.
   - **Without a Monitor:** pick delay based on what was observed.
   - Pass the original `/loop <prompt>` verbatim as the `prompt` so the next firing re-enters the skill.
5. On `<task-notification>` wake: handle the event, then call `ScheduleWakeup` again with the same prompt and 1200–1800s delay.
6. **To stop:** omit `ScheduleWakeup` and `TaskStop` any Monitor that was armed.

### Cache-aware delay guidance

| Delay | When to use |
|---|---|
| 60–270s | Actively polling external state (CI, deploy, remote queue) — cache stays warm |
| 300s | Avoid — worst of both worlds (cache miss without amortizing it) |
| 1200–1800s | Default idle heartbeat — one cache miss buys a long wait |

---

## Cloud Schedule Offer

If the interval is **≥ 60 minutes** or the input uses daily phrasing (`every morning`, `daily`, `every day`), the skill asks whether to set up a cloud schedule (persists after session close) or keep it session-only.

- **Cloud:** delegates to the `/schedule` skill.
- **Session only:** proceeds with `CronCreate` as normal.

---

## Examples

```
/loop 5m /babysit-prs              → every 5 min, run /babysit-prs
/loop check the deploy every 20m   → every 20 min, check the deploy
/loop run tests every 5 minutes    → every 5 min, run tests
/loop check the deploy             → dynamic mode, self-paced
/loop check every PR               → dynamic mode ("every PR" is not a time)
/loop 5m                           → empty prompt → shows usage
```

---

## Cancellation

```
CronDelete <job-id>
```

The job ID is shown in the confirmation message when the loop is scheduled.
