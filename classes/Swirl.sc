// =====================================================================
// Swirl - chorus, ensemble, dimension, flanger, thru-zero, barber pole, vibrato
//
// A chorus is a copy of the sound a few milliseconds late, whose delay keeps
// moving. A delay that grows reads the sound slower than it was written, so its
// pitch drops; one that shrinks reads it faster, so its pitch rises. Mixed with
// the dry, the moving copy detunes against it, and the two make a comb whose
// teeth wander: the shimmer. Every mode here is the same few lines - up to four
// delay taps a side, one lfo, a formula per mode for each tap's delay and gain,
// feedback in the tap - set up the way one piece of hardware set it up:
//
//   chorus      the Juno's two lines on one triangle, the right one upside down
//   ensemble    three lines a side, 120 degrees apart (Solina, RS-202)
//   dimension   long, slow, small sweeps, the bass kept dry (Roland SDD-320)
//   flanger     one short line swept wide, with feedback (A/DA, Electric Mistress)
//   thruzero    the dry delayed too, the wet crossing it: tape flanging
//   barberpole  four lines on one saw: notches that climb for ever
//   vibrato     only the moving copy, its swing in cents
//
//   Ndef(\pad, { Swirl.ar(Saw.ar([110, 110.3]).sum * 0.1 ! 2, \swirl) }).play;
//   p = Swirl(Ndef(\pad), \swirl);
//   p.gui;
//   p.preset = 'juno II';
//   p.style = \flanger; p.feedback = 0.7; p.sweep = 0.008; p.baseTime = 0.0015;
//   p.preset = 'barber pole';
//
// The pitch a line gets is 1 minus how fast its delay moves: a triangle lfo of
// depth a (seconds either side) at f Hz moves it 4 a f seconds a second, so the
// line sings 4 a f higher on the way in and as much lower on the way out - two
// steady detunes, the Juno's sound. 2.1 ms at 0.5 Hz is 0.42 %, 7.3 cents.
//
// Every preset is level-matched to the dry on pink noise (within 1 dB, or
// under it where that would lift the comb's peaks past +3 dB: dimension,
// tape flange). Tones are another matter: a comb lifts some and cuts others.
// Thru-zero makes the whole insert up to 5 ms late (the dry is delayed with
// the wet). The delay, the depth and the cuts glide when they change, so a
// pattern can move them event by event without a swoop or a click. The style
// menu, the table and the preset list are append-only: node maps and Tidal's
// `n` count on the order.
// =====================================================================

Swirl : FxParamUnit {
	*controlName { ^\swirl }
	*displayClass { ^SwirlView }
	*guiClass { ^FxDeviceGUI }
	*panelClass { ^FxDevicePanel }
	*windowSize { ^Point(980, 580) }
	*insertCategory { ^\modulation }
	*usesTidalClock { ^true }
	*hasTail { ^false }

	// the modulation family's orchid-rose: the voices that waver
	*identityColour { ^FxPalette.identity(\modulation) ?? { Color(0.96, 0.58, 0.86) } }

	// ---------------------------------------------------------- the modes

	// the style menu, in node-map order: append, never reorder
	*modes { ^#[\chorus, \ensemble, \dimension, \flanger, \thruzero, \barberpole, \vibrato] }
	*shapes { ^#[\triangle, \sine, \trapezoid, \noise] }

	// per mode, by index: the box's own dry, its wet, the feedback it allows
	*modeDry  { ^#[1,    0.7, 1,   0.5,  0.5, 0.5, 0] }
	*modeWet  { ^#[0.83, 1,   0.9, 0.5,  0.5, 0.5, 1] }
	*modeFb   { ^#[0.95, 0,   0,   0.95, 0,   0.7, 0] }
	// the level trim that matches the box to the dry on pink noise, measured
	*modeTrimPink { ^#[0.6486, 0.5025, 0.8610, 0.7387, 1.1371, 0.8880, 1.0105] }
	// the same with invert on: inverting the wet changes the level a lot where
	// the delay is short (thru-zero cancels the lows), so it has its own
	*modeTrimInvPink { ^#[1.0549, 0.7342, 0.8624, 1.2350, 2.1583, 1.7467, 1.0105] }
	// A trim lifts everything, the comb's peaks too: where the lines add up in
	// phase the box gives dry + wet (the ensemble's three lines, sqrt 3 x wet).
	// The trim stops where those peaks would pass +3 dB over the input, so a
	// preset that loses its bass to a notch (tape flange) is not given +6.7 dB
	// on a tone instead. Feedback's narrow peaks are not counted
	*maxPeakGain { ^1.4125 }
	*peakOf { |m| ^this.modeDry[m] + (this.modeWet[m] * #[1, 1.7320508, 1, 1, 1, 1, 1][m]) }
	*prCap { |trims| ^trims.collect { |t, m| t.min(this.maxPeakGain / this.peakOf(m)) } }
	*modeTrim { ^this.prCap(this.modeTrimPink) }
	*modeTrimInv { ^this.prCap(this.modeTrimInvPink) }

	// the trim a setting gets: by mode and invert
	*trimOf { |settings|
		var s = this.prFull(settings), m = this.modeIndex(s[\style]);
		^if (s[\invert] and: { m != 6 }) { this.modeTrimInv[m] } { this.modeTrim[m] }
	}

	// what a display reads: lfo phase, fast lfo phase, wet level L R, the
	// delays of L taps 0-3 then R, their pitch offsets (ratio - 1), their gains
	*extrasSize { ^28 }

	*params {
		^[
			this.menu(\style, this.modes, \chorus, "style",
				"which kind of swirl: chorus, ensemble, dimension, flanger, thru-zero, barber pole, vibrato", \voices),
			this.knob(\lfoRate, 0.01, 20, \exp, 0.5, "Hz", "rate",
				"how fast the lines move, when free", lag: 0.1, section: \lfo, dec: 2, size: \big),
			this.menu(\lfoSync, FxLfo.syncNames, \free, "sync",
				"free at its rate, or one period every so many cycles of Tidal's clock", \lfo),
			this.menu(\lfoShape, this.shapes, \triangle, "shape",
				"triangle = two steady detunes (Juno), sine = a wobble, trapezoid = steadier still, noise = hands on tape reels", \lfo),
			this.knob(\lfoPhase, 0, 1, \lin, 0.5, "", "phase",
				"how far apart the sides move: 50 is opposite (Juno, Dimension), 0 together (more comb, no width)",
				box: \pct, lag: 0, section: \lfo, bipolar: false),
			this.knob(\baseTime, 0.0002, 0.03, \exp, 0.0028, "s", "delay",
				"the lines' middle delay: short = flanger, 2-5 ms = Juno, 7-10 ms = Dimension; in thru-zero the latency (0.5-5 ms)",
				box: \ms, lag: 0, section: \voices),
			this.knob(\sweep, 0, 0.01, 4, 0.0021, "s", "depth",
				"how far each line moves either side of the delay; the caption shows the cents it gives",
				box: \ms, lag: 0, section: \voices, size: \big),
			this.knob(\cents, 0, 100, \lin, 25, "ct", "depth",
				"vibrato: the pitch swing in cents, whatever the rate (at slow rates, as much as 10 ms of delay allows)", lag: 0.1, section: \voices, dec: 0, size: \big),
			this.knob(\feedback, -0.95, 0.95, \lin, 0, "", "feedback",
				"flanger, chorus, barber pole: the wet back into its line - up = ringing peaks, down = hollow (unlike the family's other feedback knobs, this one goes both ways)",
				box: \pct, lag: 0, section: \voices, size: \big),
			this.toggle(\invert, false, "invert",
				"the wet upside down: the notches move (flanger), the null at the crossing (thru-zero)", \voices),
			this.toggle(\falling, false, "falling",
				"barber pole: notches that fall for ever instead of rising", \voices),
			this.knob(\fastDepth, 0, 0.0005, 3, 0, "s", "depth",
				"a second, fast lfo on every line: the string machine's shimmer (chorus, ensemble, dimension)",
				box: \ms, section: \strings),
			this.knob(\fastRate, 2, 10, \exp, 6, "Hz", "rate",
				"the fast lfo's speed: 5-6.5 Hz in the string machines", section: \strings, dec: 2),
			this.knob(\lowCut, 20, 2000, \exp, 20, "Hz", "low cut",
				"a high pass before the lines: the bass stays dry and centred (Dimension's 80 Hz)",
				lag: 0, section: \colour, dec: 0, floorText: "off"),
			this.knob(\hiCut, 1000, 20000, \exp, 20000, "Hz", "hi cut",
				"a low pass on the wet: darker voices", lag: 0, section: \colour, dec: 0),
			this.knob(\analog, 0, 1, \lin, 0, "", "analog",
				"bucket-brigade character: the Juno's filters, a compander that breathes, soft saturation",
				box: \pct, lag: 0, section: \colour, size: \medium),
			this.knob(\noise, 0, 1, \lin, 0, "", "hiss",
				"the hiss of the chips, only while sound comes in (100 % = about -50 dBFS)",
				box: \pct, section: \colour, size: \small),
			this.knob(\cross, 0, 1, \lin, 0, "", "cross",
				"each side gets the other's wet upside down: wider; at 100 the effect vanishes in mono (mono-safe)",
				box: \pct, section: \stereo),
			this.knob(\width, 0, 2, \lin, 1, "", "width",
				"mid/side width of the result: 0 mono, 100 as it is, 200 sides doubled",
				box: \pct, section: \output, bipolar: false),
			this.mixParams(\output, 1)
		]
	}

	*devicePages {
		^[
			\swirl -> [
				(title: "voices", colour: \identity, keys: [\style, \baseTime,
					(swap: [\cents, \sweep], when: { |unit| unit.get(\style) == \vibrato }),
					\feedback, [\invert, \falling]]),
				(title: "lfo", keys: [\lfoRate, [\lfoSync, \lfoShape], \lfoPhase])
			],
			\colour -> [
				\strings,
				(title: "colour", keys: [\lowCut, \hiCut, \analog, \noise]),
				\stereo
			]
		]
	}

	// a card in a strip (FxDeviceCard): the style first - it decides what the
	// others mean - then the rate or its sync, the depth the style uses, feedback
	*cardKeys { ^[\style, [\lfoRate, \lfoSync], [\sweep, \cents], \feedback] }

	*isActive { |key, unit|
		var m = unit.get(\style);
		^switch (key,
			\feedback, { [\chorus, \flanger, \barberpole].includes(m) },
			\falling, { m == \barberpole },
			\invert, { m != \vibrato },
			\baseTime, { m != \vibrato },				// vibrato finds its own delay
			\lfoShape, { m != \barberpole },			// the barber pole is a saw
			\lfoPhase, { (unit.get(\lfoShape) != \noise) or: { m == \barberpole } },	// noise has no phase
			\lfoRate, { unit.get(\lfoSync) == \free },
			\sweep, { m != \vibrato },				// vibrato's depth is in cents
			\cents, { m == \vibrato },
			\fastDepth, { [\chorus, \ensemble, \dimension].includes(m) },
			\fastRate, { [\chorus, \ensemble, \dimension].includes(m) and: { (unit.get(\fastDepth) ? 0) > 0 } },
			\cross, { m != \vibrato },
			{ true }
		)
	}

	// what a Tidal pattern may move, event by event: everything but the level.
	// The switches go through a 24 ms dip, so they change without a click
	*tidalParams {
		^#[\style, \lfoRate, \lfoSync, \lfoShape, \lfoPhase, \baseTime, \sweep, \cents, \feedback, \invert,
			\falling, \fastDepth, \fastRate, \lowCut, \hiCut, \analog, \noise, \cross, \width, \mix]
	}

	// Append-only once published, like the table and the style menu: from Tidal,
	// `n` is an index into presetNames, so `# n 9` must keep meaning 'jet flanger'
	// (0 is \default, then these in order; 'doubler' is 14)
	*presetList {
		^[
			\default, (),
			'juno I', (analog: 0.7, noise: 0.15),
			'juno II', (lfoRate: 0.83, analog: 0.7, noise: 0.15),
			'juno I+II', (lfoRate: 8, lfoShape: \sine, sweep: 0.00035, lfoPhase: 0, analog: 0.7, noise: 0.15),
			'dimension 1', (style: \dimension, baseTime: 0.010, sweep: 0.002, lfoRate: 0.25, lowCut: 80, cross: 0.5, analog: 0.5),
			'dimension 2', (style: \dimension, baseTime: 0.0075, sweep: 0.0025, lfoRate: 0.25, lowCut: 80, cross: 0.5, analog: 0.5),
			'dimension 3', (style: \dimension, baseTime: 0.0075, sweep: 0.0015, lfoRate: 0.5, lowCut: 80, cross: 0.5, analog: 0.5),
			'string machine', (style: \ensemble, baseTime: 0.005, sweep: 0.0005, lfoShape: \sine, lfoRate: 0.6,
				fastDepth: 0.0003, fastRate: 6, analog: 0.4),
			'tri chorus', (style: \ensemble, baseTime: 0.006, sweep: 0.001, lfoRate: 0.4, lfoShape: \sine),
			'jet flanger', (style: \flanger, baseTime: 0.0015, sweep: 0.008, lfoRate: 0.12, feedback: 0.7, lfoPhase: 0),
			'filter matrix', (style: \flanger, baseTime: 0.002, sweep: 0, feedback: 0.85, lfoPhase: 0),
			'tape flange', (style: \thruzero, baseTime: 0.004, sweep: 0.0039, lfoShape: \noise, lfoRate: 0.25, invert: true),
			'barber pole', (style: \barberpole, baseTime: 0.001, sweep: 0.001, lfoRate: 0.08, feedback: 0.6),
			'vibrato', (style: \vibrato, lfoShape: \sine, lfoRate: 5.5, cents: 25, lfoPhase: 0),
			'doubler', (baseTime: 0.02, sweep: 0.0015, lfoShape: \noise, lfoRate: 0.4)
		]
	}

	// ------------------------------------------------------------ the model
	// The same formulas as the graph, in numbers, for the display and the
	// checks. settings: an Event (what is not said is the table's default).

	*prFull { |settings| ^this.fullSettings(settings ? ()) }

	*modeIndex { |style| ^this.modes.indexOf(style.asSymbol) ? 0 }

	// the lfo's rate in Hz: its own, or Tidal's clock over the sync's cycles
	*rateOf { |settings, cps = 0.5625|
		var s = this.prFull(settings), i = FxLfo.syncNames.indexOf(s[\lfoSync]) ? 0;
		^if (i == 0) { s[\lfoRate] } { cps / FxLfo.syncCycles[i] }
	}

	// thru-zero's fixed dry delay: the delay knob clipped to 0.5-5 ms
	*d0Of { |base| ^base.clip(0.0005, 0.005) }

	// how late the insert makes the sound, in seconds
	*latency { |settings|
		var s = this.prFull(settings);
		^if (s[\style] == \thruzero) { this.d0Of(s[\baseTime]) } { 0 }
	}

	// FxUnit's latency: this unit's, as it is set now - and a change of what
	// decides it (the style, the delay) says changed(\latency)
	latency { ^Swirl.latency(this.settings) }

	prChanged { |what|
		super.prChanged(what);
		if (#[\style, \baseTime, \settings].includes(what)) { this.changed(\latency) };
	}

	// the lfo's slope factor: a shape at f Hz moves k f a second at most
	*slopeOf { |shape| ^[4, 2pi, 6, 2pi][this.shapes.indexOf(shape.asSymbol) ? 0] }

	*shapeAt { |shape, ph|
		var tri = 1 - (4 * (ph.wrap(0, 1) - 0.5).abs);
		^switch (shape.asSymbol,
			\triangle, { tri },
			\sine, { (ph * 2pi).sin },
			\trapezoid, { (tri * 1.5).clip2(1) },
			{ 0 }		// noise: drawn as the middle it wanders round
		)
	}

	// the quantities the graph works out once (prShared), in numbers
	*prConsts { |s, sr = 48000, cps = 0.5625|
		var base = s[\baseTime], swp = s[\sweep], margin = 4 / sr;
		var d0 = this.d0Of(base), rate = this.rateOf(s, cps);
		var k = this.slopeOf(s[\lfoShape]);
		var m = this.modeIndex(s[\style]);
		var lim = this.modeFb[m], fb = s[\feedback].clip(lim.neg, lim);
		^(m: m, base: base, d0: d0, margin: margin, rate: rate, sr: sr,
			a: (((2 ** (s[\cents] / 1200)) - 1) / (k * rate.max(0.01))).min(0.01),
			sweepL: swp.min(base - s[\fastDepth] - margin).max(0),
			sweepT: swp.min(d0 - margin),
			lnF: (1 + (swp / base)).log,
			lnB: (1 + (swp.min(base) / base)).log,
			fb: fb, comp: (1 - fb.squared).sqrt,
			pol: 1 - (2 * s[\invert].binaryValue))
	}

	// one tap's delay and gain at lfo phase ph and fast phase phF: [d, g]
	*prTapAt { |s, k, c, i, ph, phF|
		var m = k[\m];
		var offTap = [0, i / 3, 0, 0, 0, i / 4, 0][m];
		var offSide = c * s[\lfoPhase] * [1, 1/3, 1, 1, 1, 1/4, 1][m];
		var phi = (ph + offTap + offSide).wrap(0, 1);
		var sh = this.shapeAt(s[\lfoShape], phi);
		var fast = s[\fastDepth] * ((phF + (i / 3) + (c / 6)) * 2pi).sin;
		var lin = k[\base] + (k[\sweepL] * sh) + fast;
		var u = phi + ((1 - (2 * phi)) * (1 - s[\falling].binaryValue));
		var barb = k[\base] * exp(u * k[\lnB]);
		var hann = (phi * pi).sin.squared * 0.5;
		var d, g;
		if (i == 0) {
			d = [lin, lin, lin, k[\base] * exp(sh * k[\lnF]), k[\d0] + (k[\sweepT] * sh), barb,
				k[\a] + (k[\a] * sh) + k[\margin]][m];
			g = [1, 3.sqrt.reciprocal, 1, 1, 1, hann, 1][m];
		} {
			if (i < 3) {
				d = if (m == 5) { barb } { lin };
				g = [0, 3.sqrt.reciprocal, 0, 0, 0, hann, 0][m];
			} {
				d = barb;
				g = if (m == 5) { hann } { 0 };
			}
		};
		^[d.clip(3 / k[\sr], 0.044), g]
	}

	// per side, per tap: (delay: s, cents: the pitch it sings at, gain:)
	*tapsAt { |settings, ph = 0.25, phF = 0, sr = 48000, cps = 0.5625|
		var s = this.prFull(settings), k = this.prConsts(s, sr, cps);
		var eps = 1e-5, fr = s[\fastRate];
		^2.collect { |c|
			4.collect { |i|
				var dg = this.prTapAt(s, k, c, i, ph, phF);
				// the slope, from the phase either side: one lfo phase step is
				// eps / rate seconds, in which the fast lfo moves fr times as far
				var dt = eps / k[\rate].max(1e-6);
				var lo = this.prTapAt(s, k, c, i, ph - eps, phF - (dt * fr))[0];
				var hi = this.prTapAt(s, k, c, i, ph + eps, phF + (dt * fr))[0];
				var slope = (hi - lo) / (2 * dt);
				(delay: dg[0], gain: dg[1], cents: 1200 * (1 - slope).max(1e-6).log2)
			}
		}
	}

	// the shortest and longest delay any tap reaches, in seconds
	*rangeOf { |settings, sr = 48000|
		var s = this.prFull(settings), k = this.prConsts(s, sr), fd = s[\fastDepth];
		var r = switch (k[\m],
			3, { [k[\base] * exp(k[\lnF].neg), k[\base] * exp(k[\lnF])] },
			4, { [k[\d0] - k[\sweepT], k[\d0] + k[\sweepT]] },
			5, { [k[\base], k[\base] * exp(k[\lnB])] },
			6, { [k[\margin], (2 * k[\a]) + k[\margin]] },
			{ [k[\base] - k[\sweepL] - fd, k[\base] + k[\sweepL] + fd] }
		);
		^r.clip(3 / sr, 0.044)
	}

	// the largest swing in cents, as [the lfo's, the fast lfo's]. For the noise
	// shape the lfo's figure is nominal (as a sine at the rate)
	*centsParts { |settings, cps = 0.5625|
		var s = this.prFull(settings), k = this.prConsts(s, 48000, cps);
		var kk = this.slopeOf(s[\lfoShape]), f = k[\rate], slope, fast = 0;
		var cents = { |x| 1200 * (1 + x).log2 };
		slope = switch (k[\m],
			3, {
				// the exponential sweep is steepest at the long end: scan the phase
				(0..1023).collect { |j|
					var p = j / 1024, e = 1e-4;
					var d = { |q| k[\base] * exp(this.shapeAt(s[\lfoShape], q) * k[\lnF]) };
					((d.(p + e) - d.(p - e)) / (2 * e)).abs * f
				}.maxItem.max(
					// a noise shape: nominal, as a sine
					if (s[\lfoShape] == \noise) { k[\base] * exp(k[\lnF]) * k[\lnF] * 2pi * f } { 0 })
			},
			4, { k[\sweepT] * kk * f },
			5, { k[\base] * exp(k[\lnB]) * k[\lnB] * f },
			6, { k[\a] * kk * f },
			{ k[\sweepL] * kk * f }
		);
		if ([0, 1, 2].includes(k[\m])) { fast = 2pi * s[\fastDepth] * s[\fastRate] };
		^[cents.(slope), cents.(fast)]
	}

	// the largest swing, both lfos together
	*centsOf { |settings, cps = 0.5625|
		var s = this.prFull(settings), k = this.prConsts(s, 48000, cps);
		var parts = this.centsParts(s, cps);
		^1200 * (1 + ((2 ** (parts[0] / 1200)) - 1) + ((2 ** (parts[1] / 1200)) - 1)).log2
	}

	// The box's magnitude response for one side at its taps' delays (one side
	// of tapsAt), the colour stages and cross left out. A ratio per frequency.
	*response { |settings, taps, freqs|
		var s = this.prFull(settings), k = this.prConsts(s);
		var m = k[\m], gd = this.modeDry[m], gw = this.modeWet[m], trim = this.trimOf(s);
		var fb = k[\fb], amt = k[\pol] * gw * k[\comp];
		^freqs.collect { |f|
			var w = 2pi * f, re = 0, im = 0, dr, di;
			taps.do { |t|
				var ph = w * t[\delay], c = ph.cos, sn = ph.sin.neg;	// e^-jwd
				var er = 1 - (fb * c), ei = (fb * sn).neg, ee = er.squared + ei.squared;
				var nr = t[\gain] * c, ni = t[\gain] * sn;
				re = re + (((nr * er) + (ni * ei)) / ee);
				im = im + (((ni * er) - (nr * ei)) / ee);
			};
			if (m == 4) {
				dr = gd * (w * k[\d0]).cos; di = gd * (w * k[\d0]).sin.neg;
			} {
				dr = gd; di = 0;
			};
			(trim * ((dr + (amt * re)).squared + (di + (amt * im)).squared).sqrt)
		}
	}

	// the first notch and the first peak of that response above 20 Hz, in Hz
	// (nil when there is none below 20 kHz)
	*combPoints { |settings, taps|
		var freqs = (0..1999).collect { |j| 20 * (1000 ** (j / 1999)) };
		var mags = this.response(settings, taps, freqs);
		var notch, peak;
		(1..(freqs.size - 2)).do { |j|
			if (notch.isNil and: { mags[j] < mags[j - 1] } and: { mags[j] <= mags[j + 1] }) { notch = freqs[j] };
			if (peak.isNil and: { mags[j] > mags[j - 1] } and: { mags[j] >= mags[j + 1] }) { peak = freqs[j] };
		};
		^(notch: notch, peak: peak)
	}

	*firstNotch { |settings, taps|
		taps = taps ?? { this.tapsAt(settings)[0] };
		^this.combPoints(settings, taps)[\notch]
	}

	// a frequency as the nearest note and how far from it: ["B4", 21]
	*noteOf { |freq|
		var midi = 69 + (12 * (freq.max(1e-3) / 440).log2), n = midi.round.asInteger;
		var names = #["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
		^[names[n % 12] ++ ((n div: 12) - 1), ((midi - n) * 100).round.asInteger]
	}

	*prFix { |x, dec = 1|
		var n = (x.abs * (10 ** dec)).round.asInteger, str = n.asString;
		if (dec > 0) {
			str = str.padLeft(dec + 1, "0");
			str = str.drop(dec.neg) ++ "." ++ str.keep(dec.neg);
		};
		^if (x < 0 and: { n > 0 }) { "-" ++ str } { str }
	}

	*prMs { |sec|
		var ms = sec * 1000;
		^this.prFix(ms, case { ms < 1 } { 2 } { ms < 10 } { 1 } { 0 })
	}

	*prHz { |hz| ^this.prFix(hz, if (hz < 1) { 2 } { 1 }) ++ " Hz" }

	*prSyncText { |sync|
		^(four: "four cycles", two: "two cycles", one: "one cycle", half: "half a cycle",
			quarter: "a quarter cycle", eighth: "an eighth", sixteenth: "a sixteenth",
			third: "a third", sixth: "a sixth", twelfth: "a twelfth")[sync.asSymbol] ? sync.asString
	}

	*prNoteText { |freq| var n = this.noteOf(freq); ^n[0] + (if (n[1] >= 0) { "+" } { "" } ++ n[1]) + "ct" }

	// what the display's caption says: the mode, its numbers
	*caption { |settings, cps = 0.5625|
		var s = this.prFull(settings), k = this.prConsts(s, 48000, cps);
		var parts = this.centsParts(s, cps), r = this.rangeOf(s);
		var synced = s[\lfoSync] != \free;
		var rateText = if (synced) { this.prSyncText(s[\lfoSync]) } { this.prHz(k[\rate]) };
		var ct = { |c| if (s[\lfoShape] == \noise) { "≈ ±" } { "±" } ++ this.prFix(c, 1) ++ " ct" };
		var fbText = "fb " ++ if (k[\fb] > 0) { "+" } { "" } ++ (k[\fb] * 100).round.asInteger ++ " %";
		var fastText = if (s[\fastDepth] > 0) {
			" + ±" ++ this.prFix(parts[1], 1) ++ " ct at " ++ this.prHz(s[\fastRate])
		} { "" };
		^switch (s[\style],
			\chorus, { "chorus · % ± % ms · %% · %".format(this.prMs(k[\base]), this.prMs(k[\sweepL]), ct.(parts[0]), fastText, rateText) },
			\ensemble, {
				"ensemble · 3 voices a side · %%".format(ct.(parts[0]),
					if (s[\fastDepth] > 0) { fastText } { " · " ++ rateText })
			},
			\dimension, { "dimension · % ± % ms · %%".format(this.prMs(k[\base]), this.prMs(k[\sweepL]), ct.(parts[0]), fastText) },
			\flanger, {
				if (s[\sweep] <= 0) {
					// the comb's pitch is its first peak: 1 / d, or 1 / (2 d) with the
					// feedback negative (or, with none, the wet inverted)
					var pk = this.combPoints(s, this.tapsAt(s)[0])[\peak];
					var half = pk.notNil and: { (pk * k[\base]) < 0.75 };
					"flanger · comb at % · %".format(this.prNoteText((if (half) { 0.5 } { 1 }) / k[\base]), fbText)
				} {
					"flanger · %-% ms · %".format(this.prMs(r[0]), this.prMs(r[1]), fbText)
				}
			},
			\thruzero, {
				"thru-zero · % ms late · %%".format(this.prMs(k[\d0]),
					if (k[\pol] < 0) { "null at the crossing" } { "doubled at the crossing" },
					case
					{ k[\base] > 0.005 } { " · delay clipped to 5 ms" }
					{ k[\base] < 0.0005 } { " · delay clipped to 0.5 ms" }
					{ "" })
			},
			\barberpole, {
				"barber pole % · %-% ms · % a sweep".format(if (s[\falling]) { "down" } { "up" },
					this.prMs(r[0]), this.prMs(r[1]),
					if (synced) { rateText } { this.prFix(1 / k[\rate].max(1e-3), 1) ++ " s" })
			},
			\vibrato, {
				// the swing is the cents asked for, unless the delay it needs would
				// pass 10 ms either side (slow rates): then what it really is
				if (parts[0] < (s[\cents] - 0.05)) {
					"vibrato · ±% ct (10 ms, the most it can move) · %".format(this.prFix(parts[0], 1), rateText)
				} {
					"vibrato · ±% ct · %".format(s[\cents].round.asInteger, rateText)
				}
			},
			{ s[\style].asString }
		)
	}

	// ------------------------------------------------------------- the DSP

	// The switches, through a 24 ms dip to the dry, read 12 ms late so they
	// change at the dip's bottom. The late copy starts at the control's own
	// value: a bare DelayN.kr gives 0 for its first 12 ms
	*prSwitches { |p|
		var keys = #[\style, \lfoShape, \lfoSync, \invert, \falling];
		var changed = keys.collect { |k| Changed.kr(p[k]) }.sum;
		var dip = EnvGen.kr(Env([1, 0, 1], [0.012, 0.012], \sin), changed > 0);
		var late = { |x| var x0 = Latch.kr(x, Impulse.kr(0)); DelayN.kr(x - x0, 0.02, 0.012) + x0 };
		^(dip: dip, m: late.(p[\style]).round, mNow: p[\style].round, shape: late.(p[\lfoShape]).round,
			sync: late.(p[\lfoSync]).round, pol: 1 - (2 * late.(p[\invert]).round),
			falling: late.(p[\falling]).round)
	}

	// The lfo's phase at audio rate, 0..1 - free, or on Tidal's cycle (FxLfo's
	// three shared controls) - and its rate in Hz. A clock message lands on a
	// block boundary, up to a block off where the phase was heading; that step
	// is caught at the trigger's sample and faded out (150 ms), not jumped
	*prPhase { |p, sw|
		var cps = NamedControl.kr(\fxCps, 0.5625);
		var cycle = NamedControl.kr(\fxCycle, 0);
		var trig = NamedControl.tr(\fxSync, 0);
		var cycles = Select.kr(sw[\sync], FxLfo.syncCycles);
		var isSync = sw[\sync] > 0;
		var rate = Select.kr(isSync, [p[\lfoRate], cps / cycles]);
		var free = Phasor.ar(0, p[\lfoRate] * SampleDur.ir, 0, 1);
		// the clock's cycle, from the last message on, wrapped at 4: every sync's
		// period divides 4, so the wrap never moves the phase. The message's cycle
		// goes in as the Phasor's reset position, read once at the reset: added to
		// an audio signal, a control would be ramped across the block, and the
		// phase would slide a block's worth instead of stepping at one sample
		var pos = Phasor.ar(trig, cps * SampleDur.ir, 0, 4, cycle);
		var synced = pos / cycles;
		var raw = Select.ar(isSync, [free, synced]);
		var expected = Delay1.ar(raw) + (rate * SampleDur.ir);
		var step = (raw - expected).wrap(-0.5, 0.5) * T2A.ar(trig) * isSync;
		var corr = Integrator.ar(step, exp(-1 / (this.clockGlide * SampleRate.ir)));
		^[(raw - corr).wrap(0, 1), rate]
	}

	// the time constant a clock step is faded out with, in seconds
	*clockGlide { ^0.15 }

	// How a changed setting reaches the sound. A delay that moves at v seconds
	// a second sings 1 - v, so the delay and the depth glide at most
	// glideSpeed (2.5 %, 44 cents) - a jump from 1 to 10 ms takes 0.36 s, never
	// a swoop. The cuts glide in octaves (a Lag in Hz leapt 3.5 octaves in its
	// first block and the two filters rang), analog slowly enough for the
	// expander's detector to keep up with the compressor. Feedback glides
	// (prShared): a CombC keeps input + feedback x output in its line, so a
	// step in the feedback is heard again one delay later, as a click (0.024 on
	// a 0.5 sine when 'jet flanger' let go of its 0.7 in one block). The lfo
	// phase between the sides glides too: a jump of it is a jump of every R
	// line's delay
	*glideSpeed { ^0.025 }
	*cutGlide { ^0.15 }
	*analogGlide { ^0.4 }
	*fbGlide { ^0.15 }
	*phaseGlide { ^0.3 }

	*prSmooth { |p|
		var g = this.glideSpeed;
		p = p.copy;
		[\baseTime, \sweep].do { |k| p[k] = Lag.kr(Slew.kr(p[k], g, g), 0.03) };
		[\lowCut, \hiCut].do { |k| p[k] = Lag2.kr(p[k].log, this.cutGlide).exp };
		p[\analog] = Lag2.kr(p[\analog], this.analogGlide);
		p[\lfoPhase] = Lag2.kr(p[\lfoPhase], this.phaseGlide);
		^p
	}

	// vibrato's delay may move a quarter faster than its own swing needs, and
	// never slower than 0.002 s a second: a new rate or depth is reached as fast
	// as that allows, with no blip (the depth in seconds goes as 1 / rate)
	*prVibLimit { |cents| ^(((2 ** (cents / 1200)) - 1) * 1.25) + 0.002 }

	// what every tap shares, worked out once per graph
	*prShared { |p, sw, rate|
		var base = p[\baseTime], swp = p[\sweep], fb, lim, margin = 4 * SampleDur.ir;
		var d0 = base.clip(0.0005, 0.005);
		var k = Select.kr(sw[\shape], [4, 2pi, 6, 2pi]);
		var a = (((2 ** (p[\cents] / 1200)) - 1) / (k * rate.max(0.01))).min(0.01);
		var m = sw[\m], an, fbOn;
		lim = Select.kr(m, this.modeFb);
		fb = Lag2.kr(p[\feedback].clip(lim.neg, lim), this.fbGlide);
		// A mode without feedback cannot wait for the glide (a vibrato reading
		// the old resonance at 19 ms doubled a sine), nor take the step (the
		// line's content steps, and the long delay reads past it after the dip
		// has come back up: 0.084 on a 0.5 sine). So the feedback's part of each
		// line (CombC - DelayC at the same delay) fades out over the dip's way
		// down, as soon as the new style is asked for, and back in at the bottom
		fbOn = Slew.kr(BinaryOpUGen('>', lim.min(Select.kr(sw[\mNow], this.modeFb)), 0), 83.3, 83.3);
		an = ((p[\analog] - 0.002) / 0.998).max(0);						// a dead zone at 0
		^(base: base, d0: d0, a: a, aM: a + margin, margin: margin, vibLim: this.prVibLimit(p[\cents]),
			sweepL: swp.min(base - p[\fastDepth] - margin).max(0),
			sweepT: swp.min(d0 - margin),
			lnF: (1 + (swp / base)).log,
			lnB: (1 + (swp.min(base) / base)).log,
			fb: fb, fbOn: fbOn,
			// CombC's decay time per second of delay, so its coefficient is fb
			fbK: -6.907755 / fb.abs.max(1e-4).log * fb.sign,
			// flat wet level on white noise, whatever the feedback
			comp: (1 - (fb * fbOn).squared).sqrt,
			isBarb: BinaryOpUGen('==', m, 5),
			// the R side's lfo offset, as a part of a cycle (a third in the ensemble, a quarter in the barber pole)
			offSide: p[\lfoPhase] * Select.kr(m, [1, 1/3, 1, 1, 1, 1/4, 1]),
			offTap1: Select.kr(m, [0, 1/3, 0, 0, 0, 1/4, 0]),
			offTap2: Select.kr(m, [0, 2/3, 0, 0, 0, 2/4, 0]),
			gain0: Select.kr(m, [1, 3.sqrt.reciprocal, 1, 1, 1, 0, 1]),
			gain12: Select.kr(m, [0, 3.sqrt.reciprocal, 0, 0, 0, 0, 0]),
			phF: Phasor.ar(0, p[\fastRate] * SampleDur.ir, 0, 1),	// the fast lfo: one phasor, shared
			// the colour stages' amounts and corners
			analog: an,
			aAmt: (an * 20).min(1),											// filters fully in at 5 %
			lcAmt: (((p[\lowCut] / 20).log2 * 4) - 0.01).clip(0, 1),		// in by 23.8 Hz
			hcAmt: (((20000 / p[\hiCut]).log2 * 4) - 0.01).clip(0, 1),		// in by 16.8 kHz
			pole: exp(-2pi * this.prScale(7413, an) * SampleDur.ir),
			in1: this.prScale(9691, an), in2: this.prScale(10344, an),
			out1: this.prScale(8874, an), out2: this.prScale(10381, an),
			drive: 1 + (2 * an),
			compExp: an * -0.5,
			expExp: an / (2 - an))
	}

	*prShape { |shape, ph, rate|
		var tri = 1 - (4 * (ph - 0.5).abs);			// -1 at 0, +1 at 0.5, as FxLfo.at
		var noise = (LFNoise2.ar(rate.max(0.01)) * 0.85).clip2(1);	// LFNoise2 overshoots 1
		^Select.ar(shape, [tri, (ph * 2pi).sin, (tri * 1.5).clip2(1), noise])
	}

	// tap i (0..3) of side c (0, 1): [delay in seconds, gain], both audio rate.
	// Each tap works out only what its modes use: tap 0 every mode, taps 1
	// and 2 the ensemble and the barber pole, tap 3 the barber pole alone
	*prTap { |p, sw, sh, ph, rate, c, i|
		var m = sw[\m], phi, s, fast, lin, barb, hann, d, g;
		phi = ph;
		if (i > 0) { phi = phi + [nil, sh[\offTap1], sh[\offTap2], sh[\isBarb] * 0.75][i] };
		if (c > 0) { phi = phi + sh[\offSide] };
		phi = phi.wrap(0, 1);
		// up: the delay falls, so the notches rise
		barb = sh[\base] * exp(Select.ar(sw[\falling], [1 - phi, phi]) * sh[\lnB]);
		hann = (phi * pi).sin.squared * 0.5;
		if (i < 3) {
			s = this.prShape(sw[\shape], phi, rate);
			fast = p[\fastDepth] * ((sh[\phF] + (i / 3) + (c / 6)) * 2pi).sin;
			lin = sh[\base] + (sh[\sweepL] * s) + fast;
		};
		case
		{ i == 0 } {
			d = Select.ar(m, [lin, lin, lin,
				sh[\base] * exp(s * sh[\lnF]),
				sh[\d0] + (sh[\sweepT] * s),
				barb,
				Slew.ar((sh[\a] * s) + sh[\aM], sh[\vibLim], sh[\vibLim])]);
			g = (hann * sh[\isBarb]) + sh[\gain0];
		}
		{ i < 3 } {
			d = Select.ar(sh[\isBarb], [lin, barb]);
			g = (hann * sh[\isBarb]) + sh[\gain12];
		}
		{
			d = barb;
			g = hann * sh[\isBarb];
		};
		^[d.clip(3 * SampleDur.ir, 0.044), g]
	}

	// the colour stages are crossfades, the input bit for bit at their floors
	*prScale { |f, a| ^(f * ((20000 / f) ** (1 - a))).min(SampleRate.ir * 0.45) }
	// rms over 30 ms (20 ms read 0.50 % THD at 100 Hz, right on the limit)
	*prEnv { |x| ^OnePole.ar(x.squared, exp(-1 / (this.envTime * SampleRate.ir))).sqrt.max(1e-3) }
	*envTime { ^0.03 }
	*prBlend { |x, y, amt| ^x + ((y - x) * amt) }

	// how loud the hiss is at noise 1, before the box's wet gain
	*hissLevel { ^0.0535 }

	// one side: [its wet, [[delay, gain] per tap]]
	*prSide { |x, p, sw, sh, ph, rate, c|
		var src, dgs, w;
		// the wet's input: the bass kept out, then the chip's input filter
		src = this.prBlend(x, HPF.ar(HPF.ar(x, p[\lowCut]), p[\lowCut]), sh[\lcAmt]);
		src = this.prBlend(src, BLowPass.ar(BLowPass.ar(OnePole.ar(src, sh[\pole]),
			sh[\in1], 1.822), sh[\in2], 0.809), sh[\aAmt]);
		src = src * ((this.prEnv(src) / 0.1) ** sh[\compExp]).min(8);		// 2:1 at analog 1, x1 at 0
		// soft ceiling: straight up to 0.5 / drive, so a -20 dBFS line stays clean
		src = src + ((((src * sh[\drive]).softclip / sh[\drive]) - src) * sh[\analog]);
		// the taps: CombC holds the feedback whatever the delay (fb 0 is a DelayC);
		// its part beyond the plain DelayC fades with fbOn (see prShared)
		dgs = 4.collect { |i| this.prTap(p, sw, sh, ph, rate, c, i) };
		w = dgs.collect { |dg|
			var dl = DelayC.ar(src, 0.045, dg[0]);
			(dl + ((CombC.ar(src, 0.045, dg[0], A2K.kr(dg[0]) * sh[\fbK]) - dl) * sh[\fbOn])) * dg[1]
		}.sum * sh[\comp];
		// the hiss, only while something comes in: silence stays silence
		w = w + (PinkNoise.ar * p[\noise] * this.hissLevel * Lag.ar(Amplitude.ar(x, 0.01, 0.5) > 0.0003, 0.3));
		// the expander undoes the compressor: exactly x1 at analog 0
		w = w * ((this.prEnv(w) / 0.1) ** sh[\expExp]).min(4);
		w = this.prBlend(w, BLowPass.ar(BLowPass.ar(w, sh[\out1], 1.846), sh[\out2], 0.806), sh[\aAmt]);
		w = this.prBlend(w, LPF.ar(LPF.ar(w, p[\hiCut]), p[\hiCut]), sh[\hcAmt]);
		^[w, dgs]
	}

	*prGains { |sw|
		var inv = (1 - sw[\pol]) * 0.5;				// invert, as read through the dip
		^[Select.kr(sw[\m], this.modeDry), Select.kr(sw[\m], this.modeWet),
			Select.kr(sw[\m], this.modeTrim) + ((Select.kr(sw[\m], this.modeTrimInv) - Select.kr(sw[\m], this.modeTrim)) * inv)]
	}

	// a pair of channels: [[out L, out R], [dry L, dry R], extras or nil]
	*prPair { |l, r, p, sw, sh, ph, rate, wantExtras|
		var m = sw[\m], xs = [l, r];
		var sides = 2.collect { |c| this.prSide(xs[c], p, sw, sh, ph, rate, c) };
		var w = sides.collect(_[0]);
		var k = p[\cross] * (m < 6);										// not in vibrato
		var wx = [w[0] - (k * w[1]), w[1] - (k * w[0])];
		var isThru = BinaryOpUGen('==', m, 4);
		var dryD = xs.collect { |x| DelayC.ar(x, 0.006, sh[\d0]) };
		var g = this.prGains(sw);
		var box = 2.collect { |c|
			((g[0] * Select.ar(isThru, [xs[c], dryD[c]])) + (sw[\pol] * g[1] * wx[c])) * g[2]
		};
		var mid = (box[0] + box[1]) * 0.5, side = (box[0] - box[1]) * 0.5 * p[\width];
		var thruLag = Lag.kr(isThru, 0.05);
		var dry = 2.collect { |c| SelectX.ar(thruLag, [xs[c], dryD[c]]) };
		var out = [mid + side, mid - side];
		// a switch dips to the dry, not to silence
		out = 2.collect { |c| dry[c] + ((out[c] - dry[c]) * sw[\dip]) };
		^[out, dry, if (wantExtras) { this.prExtras(ph, sh, w, sides) }]
	}

	// one channel on its own: no cross, no width
	*prMono { |x, p, sw, sh, ph, rate|
		var w = this.prSide(x, p, sw, sh, ph, rate, 0)[0];
		var isThru = BinaryOpUGen('==', sw[\m], 4);
		var dryD = DelayC.ar(x, 0.006, sh[\d0]);
		var g = this.prGains(sw);
		var box = ((g[0] * Select.ar(isThru, [x, dryD])) + (sw[\pol] * g[1] * w)) * g[2];
		var dry = SelectX.ar(Lag.kr(isThru, 0.05), [x, dryD]);
		^[dry + ((box - dry) * sw[\dip]), dry]
	}

	// what the display reads, of pair 0: listening only, the sound never sees it
	*prExtras { |ph, sh, w, sides|
		var taps = sides.collect(_[1]).flatten(1);		// L 0-3, R 0-3: [delay, gain]
		^[A2K.kr(ph), A2K.kr(sh[\phF])]
			++ w.collect { |x| Amplitude.kr(x, 0.002, 0.06) }
			++ taps.collect { |dg| A2K.kr(dg[0]) }
			++ taps.collect { |dg| A2K.kr(Slope.ar(dg[0]).neg) }
			++ taps.collect { |dg| A2K.kr(dg[1]) }
	}

	*process { |sig, p, ctx|
		var n = sig.size, sw, ph, rate, sh, outs = [], drys = [], extras;
		p = this.prSmooth(p);
		sw = this.prSwitches(p);
		#ph, rate = this.prPhase(p, sw);
		sh = this.prShared(p, sw, rate);
		(n div: 2).do { |k|
			var r = this.prPair(sig[k * 2], sig[k * 2 + 1], p, sw, sh, ph, rate, k == 0);
			outs = outs ++ r[0];
			drys = drys ++ r[1];
			if (k == 0) { extras = r[2] };
		};
		if (n.odd) {
			var r = this.prMono(sig.last, p, sw, sh, ph, rate);
			outs = outs.add(r[0]);
			drys = drys.add(r[1]);
		};
		ctx[\dry] = drys;
		ctx[\extras] = extras ?? { 0 ! this.extrasSize };
		^outs
	}
}
