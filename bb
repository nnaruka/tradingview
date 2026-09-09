//@version=6
// 2026-09-09: New file — BB low-touch + 15m/1H MACD confirmation, bullish-only signal.
// Applied on a 3m or 5m chart (both map to TF+1=15m/TF+2=1h per CLAUDE.md's Timeframe
// Notation table) — general TF/TF+1/TF+2 resolution reused from the rest of this project,
// not hardcoded to "3", so it degrades safely (warning label) on other timeframes.
//
// Conditions (all ANDed):
//   1. Base TF (3m/5m): candle low touches/breaks the lower Bollinger Band (stddev x2).
//   2. TF+1 (15m): PCO state, AND (line >= 0 OR line >= its own 100-bar histogram low)
//      — i.e. PCO state, and if currently below zero, not already below its recent
//      histogram floor (a not-already-overextended filter).
//   3. TF+2 (1H): PCO state OR a PCO event happening right now.
//
// 2026-09-09: Added the exact bearish mirror (BB upper touch + 15m NCO state + 1H NCO),
// in the same file — same convention as 15m1h_engulf_signal.pine.
indicator("BB Low + 15m/1H MACD Signal", overlay=true)

// ════════════════════════════════════════════════════════════════════════════════
// SETTINGS
// ════════════════════════════════════════════════════════════════════════════════
bbLength = input.int(20, title="BB Length", minval=1, group="Bollinger Band")
bbMult   = input.float(2.0, title="BB StdDev", minval=0.1, step=0.1, group="Bollinger Band")
showDiag = input.bool(false, title="Show Diagnostic Table", group="Diagnostic")

// ════════════════════════════════════════════════════════════════════════════════
// BASE TF (chart timeframe) — Bollinger Band, direct, no security() needed
// ════════════════════════════════════════════════════════════════════════════════
bbBasis    = ta.sma(close, bbLength)
bbStdevVal = ta.stdev(close, bbLength)
bbLower    = bbBasis - bbMult * bbStdevVal
bbUpper    = bbBasis + bbMult * bbStdevVal

baseCond     = low <= bbLower
baseCondBear = high >= bbUpper

// ════════════════════════════════════════════════════════════════════════════════
// TF+1 / TF+2 RESOLUTION — from CLAUDE.md Timeframe Notation table
// ════════════════════════════════════════════════════════════════════════════════
tf1 = timeframe.period == "1" ? "3" : timeframe.period == "3" ? "15" : timeframe.period == "5" ? "15" : timeframe.period == "15" ? "60" : timeframe.period == "60" ? "240" : na
tf2 = timeframe.period == "1" ? "15" : timeframe.period == "3" ? "60" : timeframe.period == "5" ? "60" : timeframe.period == "15" ? "240" : timeframe.period == "60" ? "960" : na
supported = not na(tf1)

// ════════════════════════════════════════════════════════════════════════════════
// TF+1 (15m) MACD — line, signal, and 100-bar histogram low
// ════════════════════════════════════════════════════════════════════════════════
f_15m_data() =>
    [_l, _s, _h] = ta.macd(close, 12, 26, 9)
    [_l, _s, ta.lowest(_h, 100), ta.highest(_h, 100)]

[line15, signal15, histLowest15, histHighest15] = request.security(syminfo.tickerid, supported ? tf1 : timeframe.period, f_15m_data(), lookahead=barmerge.lookahead_on)

pcoState15 = line15 > signal15
ncoState15 = line15 < signal15
tf1Cond     = pcoState15 and (line15 >= 0 or line15 >= histLowest15)
tf1CondBear = ncoState15 and (line15 <= 0 or line15 <= histHighest15)

// ════════════════════════════════════════════════════════════════════════════════
// TF+2 (1H) MACD — PCO state or a PCO event now
// ════════════════════════════════════════════════════════════════════════════════
f_1h_data() =>
    [_l, _s, _h] = ta.macd(close, 12, 26, 9)
    [_l > _s, ta.crossover(_l, _s), _l < _s, ta.crossunder(_l, _s)]

[pcoState1h, pco1h, ncoState1h, nco1h] = request.security(syminfo.tickerid, supported ? tf2 : timeframe.period, f_1h_data(), lookahead=barmerge.lookahead_on)

tf2Cond     = pcoState1h or pco1h
tf2CondBear = ncoState1h or nco1h

// ════════════════════════════════════════════════════════════════════════════════
// FINAL SIGNAL
// ════════════════════════════════════════════════════════════════════════════════
finalSignal     = supported and baseCond and tf1Cond and tf2Cond
finalSignalBear = supported and baseCondBear and tf1CondBear and tf2CondBear

// ════════════════════════════════════════════════════════════════════════════════
// VISUAL
// ════════════════════════════════════════════════════════════════════════════════
plot(bbLower, title="BB Lower", color=color.new(color.blue, 40), linewidth=1)
plot(bbUpper, title="BB Upper", color=color.new(color.blue, 40), linewidth=1)

plotshape(finalSignal, title="BB Low + 15m/1H Signal", style=shape.triangleup, location=location.belowbar, color=color.green, size=size.tiny)
plotshape(finalSignalBear, title="BB High + 15m/1H Signal", style=shape.triangledown, location=location.abovebar, color=color.red, size=size.tiny)

// ════════════════════════════════════════════════════════════════════════════════
// ALERT
// ════════════════════════════════════════════════════════════════════════════════
if finalSignal
    alert("🟢 BB Low + 15m/1H — " + syminfo.ticker + " (" + timeframe.period + "m)", alert.freq_once_per_bar_close)

if finalSignalBear
    alert("🔴 BB High + 15m/1H — " + syminfo.ticker + " (" + timeframe.period + "m)", alert.freq_once_per_bar_close)

// ════════════════════════════════════════════════════════════════════════════════
// UNSUPPORTED TIMEFRAME WARNING
// ════════════════════════════════════════════════════════════════════════════════
if not supported and barstate.islast
    label.new(bar_index, high, "Unsupported timeframe — use 3m or 5m", color=color.orange, textcolor=color.black, style=label.style_label_down, size=size.small)

// ════════════════════════════════════════════════════════════════════════════════
// DIAGNOSTIC TABLE
// ════════════════════════════════════════════════════════════════════════════════
var table diagTable = table.new(position.top_right, 3, 18, bgcolor=color.new(color.yellow, 10), border_width=1)

fv(v) => str.tostring(v, "#.#####")
b(v)   => v ? "T" : "F"
bc(v)  => v ? color.new(color.lime, 20) : color.new(color.red, 40)

if showDiag and barstate.islast
    table.cell(diagTable, 0, 0, "Variable", text_color=color.black, text_size=size.small, bgcolor=color.new(color.orange, 10))
    table.cell(diagTable, 1, 0, "Value", text_color=color.black, text_size=size.small, bgcolor=color.new(color.orange, 10))
    table.cell(diagTable, 2, 0, "Note", text_color=color.black, text_size=size.small, bgcolor=color.new(color.orange, 10))

    table.cell(diagTable, 0, 1, "TF / TF+1 / TF+2", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 1, timeframe.period + " / " + (supported ? tf1 : "n/a") + " / " + (supported ? tf2 : "n/a"), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 1, b(supported), bgcolor=bc(supported), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 2, "baseCond (low <= BB lower)", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 2, b(baseCond), bgcolor=bc(baseCond), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 2, "low=" + fv(low) + " bbLower=" + fv(bbLower), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 3, "pcoState15", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 3, b(pcoState15), bgcolor=bc(pcoState15), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 3, "line=" + fv(line15) + " sig=" + fv(signal15), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 4, "line15 >= 0 or >= histLowest15", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 4, b(line15 >= 0 or line15 >= histLowest15), bgcolor=bc(line15 >= 0 or line15 >= histLowest15), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 4, "histLowest15=" + fv(histLowest15), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 5, "tf1Cond (15m FINAL)", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 5, b(tf1Cond), bgcolor=bc(tf1Cond), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 5, "", text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 6, "pcoState1h / pco1h", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 6, b(pcoState1h) + " / " + b(pco1h), bgcolor=bc(tf2Cond), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 6, "tf2Cond=" + b(tf2Cond), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 7, "finalSignal (FINAL)", text_color=color.black, text_size=size.small, bgcolor=color.new(color.purple, 60))
    table.cell(diagTable, 1, 7, b(finalSignal), bgcolor=bc(finalSignal), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 7, "", text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 8, "baseCondBear (high >= BB upper)", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 8, b(baseCondBear), bgcolor=bc(baseCondBear), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 8, "high=" + fv(high) + " bbUpper=" + fv(bbUpper), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 9, "ncoState15", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 9, b(ncoState15), bgcolor=bc(ncoState15), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 9, "line=" + fv(line15) + " sig=" + fv(signal15), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 10, "line15 <= 0 or <= histHighest15", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 10, b(line15 <= 0 or line15 <= histHighest15), bgcolor=bc(line15 <= 0 or line15 <= histHighest15), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 10, "histHighest15=" + fv(histHighest15), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 11, "tf1CondBear (15m FINAL)", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 11, b(tf1CondBear), bgcolor=bc(tf1CondBear), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 11, "", text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 12, "ncoState1h / nco1h", text_color=color.black, text_size=size.small)
    table.cell(diagTable, 1, 12, b(ncoState1h) + " / " + b(nco1h), bgcolor=bc(tf2CondBear), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 12, "tf2CondBear=" + b(tf2CondBear), text_color=color.black, text_size=size.small)

    table.cell(diagTable, 0, 13, "finalSignalBear (FINAL)", text_color=color.black, text_size=size.small, bgcolor=color.new(color.purple, 60))
    table.cell(diagTable, 1, 13, b(finalSignalBear), bgcolor=bc(finalSignalBear), text_color=color.black, text_size=size.small)
    table.cell(diagTable, 2, 13, "", text_color=color.black, text_size=size.small)
