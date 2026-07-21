package dev.imb11.skinshuffle.ghost;

/**
 * Client-side flag for VampireSMP "ghost / haunt" mode. Toggled by the server over the
 * {@code vsmp:ghost} channel; while active the local player clips through blocks.
 */
public final class GhostState {
    /** Whether the server has put this client into ghost/haunt mode. */
    public static volatile boolean active = false;
    /** Client toggle for clip-through (default on); only matters while {@link #active}. */
    public static volatile boolean clipEnabled = true;

    /** True only when both the server has enabled haunt AND the client toggle is on. */
    public static boolean shouldClip() {
        return active && clipEnabled;
    }

    private GhostState() {}
}
