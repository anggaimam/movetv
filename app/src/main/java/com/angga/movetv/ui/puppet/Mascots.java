package com.angga.movetv.ui.puppet;

/**
 * Small helpers for scenes that show the kids. Places with a single kid alternate between Kid A and Kid B on
 * each visit, so both get screen time.
 */
public final class Mascots {

    private static int visits;
    private static long seed = 1000;

    private Mascots() { }

    /**
     * The kid for the next single-kid screen: A, B, A, B and so on.
     * @return {@link Puppet#KID_A} or {@link Puppet#KID_B}
     */
    public static int nextKid() {
        return (visits++ & 1) == 0 ? Puppet.KID_A : Puppet.KID_B;
    }

    /**
     * A new puppet of the given kid with its own random timers.
     * @param kid {@link Puppet#KID_A} or {@link Puppet#KID_B}
     * @return the puppet
     */
    public static Puppet create(int kid) {
        seed += 7919;
        return new Puppet(kid, seed * (kid == Puppet.KID_B ? 31 : 17));
    }

    /**
     * Points a puppet's eyes at a spot on the screen, or at the middle of the screen when there is none.
     * @param p the puppet
     * @param headX x of the kid's head in stage pixels
     * @param headY y of the kid's head in stage pixels
     * @param has whether there is a target (a visible cursor)
     * @param tx target x
     * @param ty target y
     */
    public static void look(Puppet p, float headX, float headY, boolean has, float tx, float ty) {
        if (!has) {
            tx = 960f;
            ty = 540f;
        }
        p.anim().lookAt((tx - headX) / 500f, (ty - headY) / 350f);
    }
}
