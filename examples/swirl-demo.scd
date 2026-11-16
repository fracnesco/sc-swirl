// Swirl - a walkthrough, one block at a time
//
// Evaluate each ( ... ) block, or each single line, in turn with the cursor
// inside it (cmd/ctrl + return). This is an editor document, not a file to
// .load. Watch the window as you go: the voices show each line's pitch, the
// comb what the lines do to the spectrum.

// ---- 1. sounds to work on, and Swirl driven from their Ndef
(
s.waitForBoot {
	Ndef(\sw, {
		var pad, loop, saw, hats, noisy, sine, pink, k;
		// 0: a saw pad
		pad = Splay.ar(LPF.ar(Saw.ar([45, 52, 57, 61, 64].midicps), 2500)) * 0.12;
		// 1: a stereo loop - kick in the middle, hats left, a chord stab spread
		k = Impulse.ar(2);
		hats = HPF.ar(WhiteNoise.ar, 7000) * Decay2.ar(Impulse.ar(8, 0.5), 0.001, 0.05) * 0.15;
		loop = Pan2.ar(SinOsc.ar(EnvGen.ar(Env.perc(0.001, 0.25), k, 100, 45)) * Decay2.ar(k, 0.001, 0.3) * 0.4)
			+ Pan2.ar(hats, -0.4)
			+ (Splay.ar(Pulse.ar([57, 60, 64].midicps, 0.3)) * Decay2.ar(Impulse.ar(1, 0.25), 0.01, 0.6) * 0.08);
		// 2: one sustained saw, the same on both sides
		saw = LPF.ar(Saw.ar(110), 3000) * 0.15 ! 2;
		// 3: noise under the hats
		noisy = (PinkNoise.ar(0.05) ! 2) + Pan2.ar(hats, 0);
		// 4: a sine
		sine = SinOsc.ar(440) * 0.2 ! 2;
		// 5: pink noise
		pink = PinkNoise.ar(0.1) ! 2;
		Swirl.ar([pad, loop, saw, noisy, sine, pink].flop.collect { |ch| Select.ar(\src.kr(0), ch) }, \swirl)
	}).play;
	~p = Swirl(Ndef(\sw), \swirl);
	~p.gui;
};
)

// ---- 2. the Juno on a pad: two steady detunes, the right side the other way round
(
Ndef(\sw).set(\src, 0);
~p.preset = 'juno I';     // 0.5 Hz, ±7.3 ct: the caption says so
)
~p.preset = 'juno II';    // 0.83 Hz, ±12 ct
~p.preset = 'juno I+II';  // an 8 Hz wobble, not inverted
~p.enabled = false;       // the dry, at the same level: the presets are level-matched
~p.enabled = true;

// the shape: a triangle holds each detune, a sine wobbles between them
~p.preset = 'juno I';
~p.lfoShape = \sine;
~p.lfoShape = \triangle;
~p.lfoPhase = 0;          // both sides together: more comb, no width
~p.lfoPhase = 0.5;

// ---- 3. the Dimension on a stereo loop: width without audible pitch
(
Ndef(\sw).set(\src, 1);
~p.preset = 'dimension 1';
)
~p.preset = 'dimension 2';
~p.preset = 'dimension 3';

// and in mono: with cross at 100 % the effect vanishes from the mono sum
(
~p.preset = 'dimension 1';
Ndef(\sw).stop;
Ndef(\fold, { Ndef.ar(\sw, 2).sum * 0.5 ! 2 }).play;   // the mono sum
)
~p.cross = 1;             // now the mono sum is the dry alone: the wets cancel
~p.cross = 0.5;
(
Ndef(\fold).clear;
Ndef(\sw).play;
)

// ---- 4. the ensemble on one sustained saw: three lines a side become a section
(
Ndef(\sw).set(\src, 2);
~p.preset = 'tri chorus';
)
~p.preset = 'string machine';   // the fast lfo's shimmer on top
~p.fastDepth = 0;               // the slow swirl alone
~p.fastDepth = 0.0003;

// ---- 5. the jet flanger on noise and hats
(
Ndef(\sw).set(\src, 3);
~p.preset = 'jet flanger';
)
~p.feedback = -0.7;       // the hollow, odd comb
~p.invert = true;         // the notches move: where the peaks were
~p.invert = false;
~p.feedback = 0.7;

// ---- 6. the filter matrix: a comb that stands still, tuned by the delay
(
Ndef(\sw).set(\src, 3);
~p.preset = 'filter matrix';   // peaks at 500, 1000, 1500 Hz: "comb at B4 +21 ct"
)
~p.baseTime = 1 / 440;          // the comb on A4
~p.baseTime = 1 / 220;          // an octave down
~p.baseTime = 1 / 330;

// ---- 7. tape flange: the dry late too, the wet crossing it - and the null
(
Ndef(\sw).set(\src, 0);
~p.preset = 'tape flange';     // 4 ms late; with invert, silence at each crossing
)
~p.invert = false;             // doubled at the crossing instead
~p.lfoShape = \sine;           // a steady sweep instead of a hand on the reel
~p.invert = true;

// ---- 8. the barber pole on noise: notches that climb for ever
(
Ndef(\sw).set(\src, 5);
~p.preset = 'barber pole';
)
~p.falling = true;        // and fall for ever
~p.lfoRate = 0.2;         // faster: 5 s a sweep

// ---- 9. vibrato on a sine: only the moving copy
(
Ndef(\sw).set(\src, 4);
~p.preset = 'vibrato';    // 5.5 Hz, ±25 ct: the depth knob says cents now
)
~p.cents = 50;
~p.lfoRate = 2;           // slower, the same cents
~p.mix = 0.5;             // with the dry back: a chorus again
~p.mix = 1;

// ---- 10. the lfo on Tidal's clock
// lfoSync makes one period every so many cycles. SuperDirtGUI tells the
// effect Tidal's tempo and cycle; here it runs at its default 0.5625 cycles
// a second until told otherwise.
(
Ndef(\sw).set(\src, 0);
~p.preset = 'jet flanger';
~p.lfoSync = \four;       // one sweep every four cycles: 7.1 s at 0.5625
)
Ndef(\sw).set(\fxCps, 0.5);     // the tempo, as SuperDirtGUI would set it: 8 s now
~p.lfoSync = \one;              // a sweep every cycle
~p.lfoSync = \free;             // back to its own rate

// ---- 11. settings kept, and carried
(
~p.saveAsPreset("demo");
~p.copySettings;
Swirl.presetNames.postln;
Swirl.removePreset("demo");
)

// ---- 12. done
(
~p.free;
Ndef(\sw).clear;
)
