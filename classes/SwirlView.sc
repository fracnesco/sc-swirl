// =====================================================================
// SwirlView - Swirl's display: the voices, and the comb they make
//
//   voices · chorus · 2.8 ± 2.1 ms · ±7.3 ct · 0.50 Hz      comb · notch 179 Hz · F3 +42 ct
//   +25 ct |    :          ________                      |  +12 |     ___     ____    __
//          | dry          |________|  <- L                |    0 |----/   \___/    \__/
//       0  |    :                                         |  -12 |        V
//          |    :          ________                       |      |
//  -25 ct  |    :         |________|  <- R (dashed)       |  -24 |
//          0.2  0.5  1    2    5    10   20 ms                     50  100 ... 10k
//   lfo  /\/\  ...  |  ...                       0.50 Hz
//
// Left, the voices: every delay line as it moves over one lfo period, its
// delay along (ms, log) and the pitch it sings at up (cents). A read pointer
// whose delay grows reads slower than the sound was written, so it sings
// flat; one whose delay shrinks sings sharp: the pitch is 1 minus how fast
// the delay moves. So each line draws a closed path - a sine an ellipse, the
// Juno's triangle two flat lines at plus and minus its cents (two steady
// detunes) joined by jumps, the ensemble three loops a side, the barber pole
// lines that fade in and out, vibrato an ellipse at the left. The left side's
// lines are drawn solid, the right side's dashed. The dots are where each
// line is now, as big as it is loud in the mix; the blue line is the dry
// (at its delay in thru-zero, where the dry is late too). The faint band is
// the range the lines sweep. Under it, the lfo over one period, with where
// it is now.
//
// Right, the comb: what the lines do to the spectrum now (the left side), the
// faint band where the notches travel as the lfo goes round, the output's
// spectrum behind. A chorus is a comb too, a shallow one whose teeth wander:
// that is the shimmer. Its first notch (or peak, with feedback in the flanger)
// is named with its note.
//
//   voices   drag along: the delay (log); up and down: the depth (cents in
//            vibrato); the wheel: the phase between the sides; double
//            click: delay and depth back to their defaults
//   comb     drag along: the delay, so the first notch follows the pointer;
//            up and down: the feedback (chorus, flanger, barber pole only);
//            the wheel: the depth; double click: feedback 0
//   lfo      drag along: the rate (free only); the wheel: the next shape;
//            double click: the rate's default
//   shift    fine, everywhere
//
// What is a model and what is measured: the paths, the band, the sweep
// range, the comb's curve and its band, the caption and the lfo curve are
// worked out from the settings (Swirl's class-side model, the same formulas
// as the sound). The dots, the lfo's playhead, the rings round the dots (the
// wet level of each side) and the spectrum are what the server reports, and
// they move only while the window runs and the effect is on; until a reply
// comes the dots sit where the model puts them a quarter of the way round.
// =====================================================================

SwirlView : FxParamView {
	// Tidal's cycles per second as the model assumes them for a synced lfo:
	// the unit does not know the clock, the server does
	classvar <>cps = 0.5625;
	classvar <>curvePoints = 160;

	var cacheKey, model, drag, dragX, dragY, dragFrom, dragShift = false;

	*wantsSpectrum { ^true }
	*minHeight { ^250 }

	*delayLo { ^0.00005 }
	*delayHi { ^0.045 }
	*dbLo { ^-24 }
	*dbHi { ^18 }

	// ------------------------------------------------------------- geometry

	prSplit { ^(bw * 0.6).round }
	prVoicesRect { ^Rect(36, 24, this.prSplit - 36 - 12, (bh - 70).max(40)) }
	prStripRect { var v = this.prVoicesRect; ^Rect(v.left, bh - 22, v.width, 18) }
	prCombRect { var x0 = this.prSplit + 32; ^Rect(x0, 24, (bw - x0 - 8).max(40), (bh - 46).max(40)) }

	prDelayX { |d, r|
		^r.left + ((d.max(this.class.delayLo) / this.class.delayLo).log
			/ (this.class.delayHi / this.class.delayLo).log * r.width)
	}
	prCentsY { |c, r, scale| ^r.center.y - ((c / scale).clip(-1.1, 1.1) * r.height * 0.46) }
	prFreqX { |f, r| ^r.left + FxParamView.freqToX(f, r.width) }
	prXFreq { |x, r| ^FxParamView.xToFreq(x - r.left, r.width) }
	prDbY { |db, r| ^db.linlin(this.class.dbLo, this.class.dbHi, r.bottom, r.top, \minmax) }

	// which part a point is in: \voices, \strip, \comb or nil
	prPartAt { |x, y|
		var p = x @ y;
		if (this.prVoicesRect.insetBy(-4, -4).containsPoint(p)) { ^\voices };
		if (this.prStripRect.insetBy(-2, -3).containsPoint(p)) { ^\strip };
		if (this.prCombRect.insetBy(-4, -4).containsPoint(p)) { ^\comb };
		^nil
	}

	// ------------------------------------------------------------ the model

	prStyle { ^unit.get(\style) }
	prDepthKey { ^if (this.prStyle == \vibrato) { \cents } { \sweep } }

	// worked out again only when a setting changes
	prModel {
		var cols = this.prCombRect.width.asInteger.max(10);
		var key = Swirl.paramNames.collect { |k| unit.get(k) } ++ [cols];
		if (key != cacheKey or: { model.isNil }) {
			cacheKey = key;
			model = this.class.modelOf(unit.settings, nil, this.class.columnFreqs(cols, 2));
			model[\curveFreqs] = this.class.columnFreqs(cols, 4);
		};
		^model
	}

	// k frequencies in each of cols columns, 20 Hz to 20 kHz (log): a comb has
	// more teeth than a display has pixels, so a column shows its lowest and
	// highest point, never one sample that may fall between two teeth
	*columnFreqs { |cols, k = 4|
		var n = cols * k;
		^(0..(n - 1)).collect { |j| 20 * (1000 ** ((j + 0.5) / n)) }
	}

	// per column [min, max] of a list of dB values, k a column
	*columns { |dbs, k = 4| ^dbs.clump(k).collect { |c| [c.minItem, c.maxItem] } }

	// Everything the display draws from the settings alone, as numbers, so
	// a check can ask it too. paths: per side, per tap, an Array of
	// [delay, cents, gain] over one lfo period; still: the taps at phase 0.25.
	*modelOf { |settings, cps, envFreqs|
		var s = Swirl.fullSettings(settings), m = Swirl.modeIndex(s[\style]);
		var noise = (s[\lfoShape] == \noise) and: { m != 5 };
		var drawS = if (noise) { s.copy.put(\lfoShape, \sine) } { s };
		var k, rate, ratio, n, raw, paths, still, scale, freqs, env, cents, span;
		cps = cps ? this.cps;
		k = Swirl.prConsts(drawS, 48000, cps);
		rate = k[\rate].max(1e-3);
		ratio = if ((s[\fastDepth] > 0) and: { m < 3 }) { s[\fastRate] / rate } { 0 };
		n = (64 * ratio.max(1)).ceil.clip(64, 384).asInteger;
		// the delays at n + 1 phases; each point of a path is the middle of a
		// step, its pitch from the step's slope, so a triangle's corner is a
		// clean jump from one detune to the other
		raw = 2.collect { |c|
			4.collect { |i|
				(n + 1).collect { |j| Swirl.prTapAt(drawS, k, c, i, j / n, j / n * ratio) }
			}
		};
		span = Swirl.rangeOf(drawS).differentiate[1];
		paths = raw.collect { |side|
			side.collect { |pts|
				n.collect { |j|
					var a = pts[j], b = pts[j + 1];
					var slope = (b[0] - a[0]) * n * rate;
					// a step across the barber pole's reset is no movement: gain 0, not drawn
					var reset = (span > 0) and: { (b[0] - a[0]).abs > (span * 0.5) };
					[(a[0] + b[0]) * 0.5, 1200 * (1 - slope).max(1e-6).log2,
						if (reset) { 0 } { (a[1] + b[1]) * 0.5 }]
				}
			}
		};
		// a noise lfo is drawn as the sine it wanders like (the caption says "≈")
		still = Swirl.tapsAt(drawS, 0.25, 0, 48000, cps);
		cents = Swirl.centsOf(s, cps);
		scale = #[10, 25, 50, 100].detect { |v| v >= (cents * 1.1) } ? 100;
		// the comb: the band the curve sweeps over 16 phases of the lfo
		freqs = envFreqs ? this.curveFreqs;
		env = 16.collect { |j|
			Swirl.response(s, Swirl.tapsAt(drawS, j / 16, 0, 48000, cps)[0], freqs).collect { |a| a.max(1e-6).ampdb }
		}.flop.collect { |dbs| [dbs.minItem, dbs.maxItem] };
		^(settings: s, mode: m, noise: noise, rate: k[\rate], ratio: ratio, paths: paths, still: still,
			scale: scale, range: Swirl.rangeOf(s), d0: k[\d0], env: env, freqs: freqs,
			caption: Swirl.caption(s, cps), cents: cents)
	}

	*curveFreqs { ^(0..(curvePoints - 1)).collect { |j| 20 * (1000 ** (j / (curvePoints - 1))) } }

	// the taps as the server reports them, or nil: per side, per tap, (delay:, cents:, gain:)
	prLiveTaps {
		var ex;
		if (unit.isAlive.not) { ^nil };
		ex = unit.extras ? [];
		if (ex.size < Swirl.extrasSize) { ^nil };
		^2.collect { |c|
			4.collect { |i|
				var j = (c * 4) + i;
				(delay: ex[4 + j], cents: 1200 * (1 + ex[12 + j]).max(1e-6).log2, gain: ex[20 + j])
			}
		}
	}

	prTaps { ^this.prLiveTaps ?? { this.prModel[\still] } }

	prPhaseNow {
		var ex = unit.extras ? [];
		^if (unit.isAlive and: { ex.size >= 2 }) { ex[0].wrap(0, 1) } { 0.25 }
	}

	// the first notch, or the first peak with feedback in the flanger, of a
	// response, refined between the curve's points: [\notch or \peak, Hz] or nil
	*combMark { |settings, taps, freqs, dbs|
		var s = Swirl.fullSettings(settings), k = Swirl.prConsts(s);
		var wantPeak = (s[\style] == \flanger) and: { k[\fb] > 0 }, j0, f;
		freqs = freqs ? this.curveFreqs;
		dbs = dbs ?? { Swirl.response(s, taps, freqs).collect { |a| a.max(1e-6).ampdb } };
		// a flat line (vibrato has no dry, so nothing to comb with) has no notch
		if ((dbs.maxItem - dbs.minItem) < 0.3) { ^nil };
		(1..(freqs.size - 2)).do { |j|
			if (j0.isNil) {
				if (wantPeak) {
					if (dbs[j] > dbs[j - 1] and: { dbs[j] >= dbs[j + 1] }) { j0 = j }
				} {
					if (dbs[j] < dbs[j - 1] and: { dbs[j] <= dbs[j + 1] }) { j0 = j }
				}
			}
		};
		if (j0.isNil) { ^nil };
		// 24 points between the neighbours
		f = (0..24).collect { |q| freqs[j0 - 1] * ((freqs[j0 + 1] / freqs[j0 - 1]) ** (q / 24)) };
		dbs = Swirl.response(s, taps, f);
		^[if (wantPeak) { \peak } { \notch }, f[if (wantPeak) { dbs.maxIndex } { dbs.minIndex }]]
	}

	// ---------------------------------------------------------------- drawing

	prDraw {
		var md = this.prModel, ident = Swirl.identityColour, taps = this.prTaps;
		var dbs = Swirl.response(md[\settings], taps[0], md[\curveFreqs]).collect { |a| a.max(1e-6).ampdb };
		var mark = this.class.combMark(md[\settings], taps[0], md[\curveFreqs], dbs);
		this.prDrawTitles(md, ident, mark);
		this.prDrawVoices(md, ident, taps);
		this.prDrawStrip(md, ident);
		this.prDrawComb(md, ident, dbs, mark);
	}

	// a title in the identity colour, then its words, cut to the room there is
	prTitle { |title, words, x, room, ident|
		var font = FxPalette.font, w = title.bounds(font).width, str = "·  " ++ words, fit;
		Pen.stringAtPoint(title, x @ 4, font, ident);
		fit = room - w - 8;
		if (str.bounds(font).width > fit) { font = FxPalette.smallFont };
		while { str.size > 4 and: { str.bounds(font).width > fit } } { str = str.drop(-1) };
		Pen.stringAtPoint(str, (x + w + 6) @ (if (font == FxPalette.font) { 4 } { 6 }), font, FxPalette.text);
	}

	prDrawTitles { |md, ident, mark|
		var v = this.prVoicesRect, c = this.prCombRect;
		this.prTitle("voices", md[\caption], 8, c.left - 40, ident);
		this.prTitle("comb", mark !? { this.class.markText(mark) } ?? {
				if (md[\mode] == 6) { "no dry, no comb: only the pitch moves" } { "no notch under 20 kHz" } },
			c.left - 28, bw - c.left + 20, ident);
	}

	*markText { |mark|
		var f = mark[1], n = Swirl.noteOf(f);
		^"% % · % %% ct".format(mark[0], FxPalette.format(f, "Hz", nil, 0), n[0],
			if (n[1] >= 0) { "+" } { "" }, n[1])
	}

	prDrawVoices { |md, ident, taps|
		var r = this.prVoicesRect, scale = md[\scale], m = md[\mode], font = FxPalette.smallFont;
		var range = md[\range], x0, x1, dryX;
		Pen.fillColor = FxPalette.bg;
		Pen.addRoundedRect(r, 3, 3);
		Pen.fill;
		// the scale: cents up, ms along
		[-1, -0.5, 0, 0.5, 1].do { |f|
			var y = this.prCentsY(f * scale, r, scale);
			Pen.fillColor = FxPalette.fade(FxPalette.waveDim, if (f == 0) { 0.8 } { 0.3 });
			Pen.fillRect(Rect(r.left, y, r.width, 1));
			if (f.abs != 0.5) {
				Pen.stringRightJustIn(if (f == 0) { "0" } { "%% ct".format(if (f > 0) { "+" } { "-" }, scale) },
					Rect(0, y - 6, r.left - 4, 12), font, FxPalette.dim);
			};
		};
		#[0.0002, 0.0005, 0.001, 0.002, 0.005, 0.01, 0.02].do { |d, i|
			var x = this.prDelayX(d, r);
			Pen.fillColor = FxPalette.fade(FxPalette.waveDim, 0.25);
			Pen.fillRect(Rect(x, r.top, 1, r.height));
			Pen.stringCenteredIn(#[".2", ".5", "1", "2", "5", "10", "20 ms"][i],
				Rect(x - 20, r.bottom + 2, 40, 12), font, FxPalette.dim);
		};
		// the range the lines sweep
		x0 = this.prDelayX(range[0], r); x1 = this.prDelayX(range[1], r);
		Pen.fillColor = FxPalette.fade(ident, 0.08);
		Pen.fillRect(Rect(x0, r.top + 1, (x1 - x0).max(1), r.height - 2));
		// the dry: no delay, or the fixed one in thru-zero; none in vibrato
		if (m != 6) {
			dryX = if (m == 4) { this.prDelayX(md[\d0], r) } { r.left + 1 };
			Pen.fillColor = FxPalette.fade(FxPalette.wave, 0.85);
			Pen.fillRect(Rect(dryX - 0.5, r.top + 1, 1.5, r.height - 2));
			Pen.stringLeftJustIn(if (m == 4) { "dry % late".format(FxPalette.format(md[\d0], "s", \ms)) } { "dry" },
				Rect(dryX + 3, r.top + 2, 120, 12), font, FxPalette.wave);
		};
		this.prDrawPaths(md, ident, r, scale);
		if (md[\noise]) {
			Pen.stringRightJustIn("noise: the lines wander inside these paths",
				Rect(r.left, r.bottom - 14, r.width - 6, 12), font, FxPalette.dim);
		};
		this.prDrawDots(md, ident, taps, r, scale);
	}

	prDrawPaths { |md, ident, r, scale|
		var barber = md[\mode] == 5;
		// the string machine's fast ripple draws many loops: thinner
		Pen.width = if (md[\ratio] > 0) { 1 } { 1.5 };
		md[\paths].do { |side, c|
			side.do { |pts, i|
				var used = pts.any { |p| p[2] > 0.001 };
				var alpha = (if (c == 0) { 1 } { 0.6 }) * (1 - (i * 0.18)) * (if (md[\noise]) { 0.45 } { 1 });
				var col = FxPalette.fade(ident, alpha);
				if (used) {
					Pen.lineDash = if (c == 0 and: { md[\noise].not }) { FloatArray[1, 0] } { FloatArray[4, 3] };
					if (barber) {
						// alpha follows the window: the line fades in and out
						pts.do { |p, j|
							var q = pts.wrapAt(j + 1);
							// not across the reset, where the saw starts again
							if ((p[2] > 0) and: { q[2] > 0 }) {
								Pen.strokeColor = FxPalette.fade(col, (p[2] * 2).clip(0, 1));
								Pen.line(this.prDelayX(p[0], r) @ this.prCentsY(p[1], r, scale),
									this.prDelayX(q[0], r) @ this.prCentsY(q[1], r, scale));
								Pen.stroke;
							}
						}
					} {
						Pen.strokeColor = col;
						pts.do { |p, j|
							var pt = this.prDelayX(p[0], r) @ this.prCentsY(p[1], r, scale);
							if (j == 0) { Pen.moveTo(pt) } { Pen.lineTo(pt) };
						};
						Pen.lineTo(this.prDelayX(pts[0][0], r) @ this.prCentsY(pts[0][1], r, scale));
						Pen.stroke;
					}
				}
			}
		};
		Pen.lineDash = FloatArray[1, 0];
	}

	prDrawDots { |md, ident, taps, r, scale|
		var ex = unit.extras ? [], alive = this.prLiveTaps.notNil;
		taps.do { |side, c|
			side.do { |t, i|
				var g = t[\gain] ? 0, pt, rad;
				if (g > 0.001) {
					pt = this.prDelayX(t[\delay], r) @ this.prCentsY(t[\cents], r, scale);
					rad = 2.5 + (3.5 * g.clip(0, 1));
					Pen.fillColor = FxPalette.fade(ident, if (c == 0) { 1 } { 0.6 });
					Pen.addOval(Rect.aboutPoint(pt, rad, rad));
					Pen.fill;
					// the side's wet level, as a ring round its first line's dot
					if (alive and: { i == 0 }) {
						Pen.strokeColor = FxPalette.fade(FxPalette.handle,
							(ex[2 + c] ? 0).max(1e-5).ampdb.linlin(-60, 0, 0, 0.8));
						Pen.width = 1;
						Pen.addOval(Rect.aboutPoint(pt, rad + 3, rad + 3));
						Pen.stroke;
					};
				}
			}
		};
	}

	// the lfo over one period, and where it is now
	prDrawStrip { |md, ident|
		var r = this.prStripRect, s = md[\settings], m = md[\mode], font = FxPalette.smallFont;
		var n = 160, ph = this.prPhaseNow, x;
		var ripple = if (md[\ratio] > 0) { (s[\fastDepth] / 0.0005).clip(0, 1) * 0.3 } { 0 };
		var shape = s[\lfoShape], noise = md[\noise];
		var yOf = { |v| r.center.y - (v.clip(-1.3, 1.3) * (r.height * 0.5 - 2) / 1.3) };
		var valueAt = { |p|
			var v = case
			{ m == 5 } { if (s[\falling]) { p * 2 - 1 } { 1 - (p * 2) } }
			{ noise } { ((p * 2pi * 1.0).sin * 0.5) + ((p * 2pi * 2.7 + 1).sin * 0.3) + ((p * 2pi * 5.3 + 2).sin * 0.15) }
			{ Swirl.shapeAt(shape, p) };
			v + (ripple * (p * md[\ratio] * 2pi).sin)
		};
		Pen.fillColor = FxPalette.bg;
		Pen.addRoundedRect(r, 3, 3);
		Pen.fill;
		Pen.fillColor = FxPalette.fade(FxPalette.waveDim, 0.5);
		Pen.fillRect(Rect(r.left, r.center.y, r.width, 1));
		Pen.strokeColor = FxPalette.fade(ident, if (noise) { 0.5 } { 0.85 });
		Pen.width = 1.5;
		if (noise) { Pen.lineDash = FloatArray[4, 3] };
		(n + 1).do { |j|
			var p = j / n, pt = (r.left + (p * r.width)) @ yOf.(valueAt.(p));
			if (j == 0) { Pen.moveTo(pt) } { Pen.lineTo(pt) };
		};
		Pen.stroke;
		Pen.lineDash = FloatArray[1, 0];
		// now
		x = r.left + (ph * r.width);
		Pen.fillColor = FxPalette.handle;
		Pen.fillRect(Rect(x - 0.75, r.top + 1, 1.5, r.height - 2));
		Pen.stringRightJustIn("lfo", Rect(0, r.top, r.left - 4, r.height), font, FxPalette.dim);
	}

	prDrawComb { |md, ident, dbs, mark|
		var r = this.prCombRect, font = FxPalette.smallFont, fbOn = Swirl.isActive(\feedback, unit);
		var env = [this.class.columns(md[\env].collect(_[0]), 2), this.class.columns(md[\env].collect(_[1]), 2)]
			.flop.collect { |pair| [pair[0][0], pair[1][1]] };
		var now = this.class.columns(dbs, 4), n = now.size;
		var colX = { |c| r.left + ((c + 0.5) / n * r.width) };
		Pen.fillColor = FxPalette.bg;
		Pen.addRoundedRect(r, 3, 3);
		Pen.fill;
		[-24, -12, 0, 12].do { |db|
			var y = this.prDbY(db, r);
			Pen.fillColor = FxPalette.fade(FxPalette.waveDim, if (db == 0) { 0.8 } { 0.3 });
			Pen.fillRect(Rect(r.left, y, r.width, 1));
			Pen.stringRightJustIn(if (db == 0) { "0 dB" } { if (db > 0) { "+" ++ db } { db.asString } },
				Rect(r.left - 32, y - 6, 28, 12), font, FxPalette.dim);
		};
		[50, 100, 200, 500, 1000, 2000, 5000, 10000].do { |hz|
			var x = this.prFreqX(hz, r);
			Pen.fillColor = FxPalette.fade(FxPalette.waveDim, 0.25);
			Pen.fillRect(Rect(x, r.top, 1, r.height));
			Pen.stringCenteredIn(if (hz >= 1000) { (hz div: 1000).asString ++ "k" } { hz.asString },
				Rect(x - 16, r.bottom + 2, 32, 12), font, FxPalette.dim);
		};
		// what comes out, behind
		this.prDrawSpectrumIn(r);
		// where the notches travel
		Pen.fillColor = FxPalette.fade(ident, 0.14);
		env.do { |e, c| var pt = colX.(c) @ this.prDbY(e[1], r); if (c == 0) { Pen.moveTo(pt) } { Pen.lineTo(pt) } };
		env.reverseDo { |e, j| Pen.lineTo(colX.(env.size - 1 - j) @ this.prDbY(e[0], r)) };
		Pen.fill;
		// now: each column from its lowest to its highest point, so a comb
		// denser than the pixels reads as a band and a smooth curve as a line
		Pen.fillColor = ident;
		now.do { |e, c| var pt = colX.(c) @ (this.prDbY(e[1], r) - 0.75); if (c == 0) { Pen.moveTo(pt) } { Pen.lineTo(pt) } };
		now.reverseDo { |e, j| Pen.lineTo(colX.(n - 1 - j) @ (this.prDbY(e[0], r) + 0.75)) };
		Pen.fill;
		mark !? {
			var x = this.prFreqX(mark[1], r);
			Pen.strokeColor = FxPalette.fade(FxPalette.handle, 0.6);
			Pen.width = 1;
			Pen.lineDash = FloatArray[2, 2];
			Pen.line(x @ (r.top + 2), x @ (r.bottom - 2));
			Pen.stroke;
			Pen.lineDash = FloatArray[1, 0];
		};
		if (fbOn.not) {
			Pen.stringRightJustIn("no feedback in %".format(md[\settings][\style]),
				Rect(r.left, r.top + 2, r.width - 6, 12), font, FxPalette.dim);
		};
	}

	// the output's spectrum, filled, inside a rectangle
	prDrawSpectrumIn { |r|
		var levels, freqs, lo = ParaEQSpectrum.floorDb, hi = ParaEQSpectrum.topDb;
		if (spectrum.isNil or: { spectrum.visible.not }) { ^this };
		levels = spectrum.levels;
		freqs = spectrum.freqs;
		if (levels.isNil or: { freqs.isNil }) { ^this };
		Pen.fillColor = FxPalette.fade(FxPalette.wave, 0.22);
		Pen.moveTo(r.left @ r.bottom);
		levels.do { |db, i|
			Pen.lineTo(this.prFreqX(freqs[i].clip(20, 20000), r) @ db.linlin(lo, hi, r.bottom, r.top, \minmax));
		};
		Pen.lineTo(r.right @ r.bottom);
		Pen.fill;
	}

	// --------------------------------------------------------------- playing

	prNudge { |key, from, amount|
		var spec = Swirl.param(key)[\spec];
		^spec.map((spec.unmap(from) + amount).clip(0, 1))
	}

	prSnapshot {
		^(baseTime: unit.get(\baseTime), sweep: unit.get(\sweep), cents: unit.get(\cents),
			feedback: unit.get(\feedback), lfoRate: unit.get(\lfoRate))
	}

	prMouseDown { |x, y, mod, count|
		var part = this.prPartAt(x, y);
		drag = nil;
		dragShift = mod.isShift;
		if (part.isNil) { ^this };
		if (count == 2) { this.prReset(part); ^this };
		drag = part;
		dragX = x;
		dragY = y;
		dragFrom = this.prSnapshot;
	}

	prReset { |part|
		switch (part,
			\voices, {
				if (Swirl.isActive(\baseTime, unit)) { unit.set(\baseTime, Swirl.param(\baseTime)[\default]) };
				unit.set(this.prDepthKey, Swirl.param(this.prDepthKey)[\default]);
			},
			\comb, { if (Swirl.isActive(\feedback, unit)) { unit.set(\feedback, 0) } },
			\strip, { if (Swirl.isActive(\lfoRate, unit)) { unit.set(\lfoRate, Swirl.param(\lfoRate)[\default]) } }
		)
	}

	prMouseMove { |x, y, mod|
		var f, dx, dy, r, key;
		if (drag.isNil) { ^this };
		// shift pressed or let go mid-drag: go on from here, at the new speed
		if (mod.isShift != dragShift) { dragShift = mod.isShift; dragFrom = this.prSnapshot; dragX = x; dragY = y };
		f = if (dragShift) { 0.1 } { 1 };
		dx = (x - dragX) * f;
		dy = (dragY - y) * f;
		switch (drag,
			\voices, {
				r = this.prVoicesRect;
				if (Swirl.isActive(\baseTime, unit)) {
					unit.set(\baseTime, dragFrom[\baseTime] * ((this.class.delayHi / this.class.delayLo) ** (dx / r.width)))
				};
				key = this.prDepthKey;
				unit.set(key, this.prNudge(key, dragFrom[key], dy / 200));
			},
			\comb, {
				r = this.prCombRect;
				// the first notch is at 1 / (2 d): a notch twice as high is half the delay
				if (Swirl.isActive(\baseTime, unit)) {
					unit.set(\baseTime, dragFrom[\baseTime] / (1000 ** (dx / r.width)))
				};
				if (Swirl.isActive(\feedback, unit)) { unit.set(\feedback, dragFrom[\feedback] + (dy / 200 * 1.9)) };
			},
			\strip, {
				r = this.prStripRect;
				if (Swirl.isActive(\lfoRate, unit)) {
					unit.set(\lfoRate, this.prNudge(\lfoRate, dragFrom[\lfoRate], dx / r.width))
				};
			}
		)
	}

	prMouseUp { drag = nil }

	prWheel { |x, y, delta|
		var part = this.prPartAt(x, y), shapes, i, key;
		if (part.isNil or: { delta == 0 }) { ^this };
		switch (part,
			\voices, {
				if (Swirl.isActive(\lfoPhase, unit)) {
					unit.set(\lfoPhase, (unit.get(\lfoPhase) + (delta.sign * 0.05)).clip(0, 1))
				}
			},
			\comb, {
				key = this.prDepthKey;
				unit.set(key, this.prNudge(key, unit.get(key), delta.sign * 0.02));
			},
			\strip, {
				if (Swirl.isActive(\lfoShape, unit)) {
					shapes = Swirl.shapes;
					i = shapes.indexOf(unit.get(\lfoShape)) ? 0;
					unit.set(\lfoShape, shapes.wrapAt(i + delta.sign.asInteger));
				}
			}
		)
	}

	hint { |x, y|
		var part, s, fmt;
		if (x.isNil or: { unit.isNil }) { ^"" };
		part = this.prPartAt(x, y);
		s = unit.settings;
		fmt = { |key| var e = Swirl.param(key);
			FxPalette.format(s[key], e[\spec].units, e[\box], e[\dec], e[\bipolar], e[\floorText], e[\spec]) };
		^switch (part,
			\voices, {
				if (s[\style] == \vibrato) {
					"voices: the moving copy's delay along, the pitch it sings at up - drag up and down for the depth (%); the wheel: phase (%); double click: back".format(fmt.(\cents), fmt.(\lfoPhase))
				} {
					"voices: each line's delay along, the pitch it sings at up, over one lfo period - drag along for the delay (%), up and down for the depth (%); the wheel: phase (%); double click: both back"
					.format(fmt.(\baseTime), fmt.(\sweep), fmt.(\lfoPhase))
				}
			},
			\comb, {
				if (Swirl.isActive(\baseTime, unit).not) {
					"comb: vibrato has no dry, so no comb; the wheel: depth (%)".format(fmt.(this.prDepthKey))
				} {
				"comb: what the lines do to the spectrum now, faint where the notches travel - drag along to move the first notch (delay %); %; the wheel: depth (%)%"
				.format(fmt.(\baseTime),
					if (Swirl.isActive(\feedback, unit)) { "up and down: feedback (%)".format(fmt.(\feedback)) }
					{ "feedback: chorus, flanger and barber pole only" },
					fmt.(this.prDepthKey),
					if (Swirl.isActive(\feedback, unit)) { "; double click: feedback 0" } { "" })
				}
			},
			\strip, {
				if (Swirl.isActive(\lfoRate, unit)) {
					"lfo: one period, and where it is now - drag along for the rate (%); the wheel: the next shape (%)"
					.format(fmt.(\lfoRate), s[\lfoShape])
				} {
					"lfo: synced to % - set sync to free to drag the rate; the wheel: the next shape (%)"
					.format(Swirl.prSyncText(s[\lfoSync]), s[\lfoShape])
				}
			},
			{ "" }
		)
	}
}
