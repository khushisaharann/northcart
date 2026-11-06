import { useEffect, useMemo, useRef, useState } from 'react';
import { formatPrice, formatPriceCompact, formatShortDate } from '../../utils/format';

const MARGIN = { top: 12, right: 16, bottom: 28, left: 56 };
const TICKS = 4;

/** Rounds the axis maximum up to a clean value: 13,420 -> 20,000 (4 ticks of 5,000). Steps are 1, 2, 2.5 or 5 x 10^n. */
export function niceMax(value) {
  if (!value || value <= 0) return 1000;
  const raw = value / TICKS;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw);
  return step * TICKS;
}

/**
 * Daily revenue as a single-series area chart, drawn in plain SVG.
 * Follows the chart spec: 2px line, ~10% area wash, hairline gridlines, compact y ticks,
 * crosshair + tooltip that snaps to the nearest day, keyboard support (← →).
 * One series, so no legend: the card title names it.
 */
export function SalesChart({ data, height = 240 }) {
  const wrapper = useRef(null);
  const [width, setWidth] = useState(640);
  const [active, setActive] = useState(null);

  useEffect(() => {
    if (!wrapper.current || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(280, entry.contentRect.width)));
    observer.observe(wrapper.current);
    return () => observer.disconnect();
  }, []);

  const geometry = useMemo(() => {
    const innerW = width - MARGIN.left - MARGIN.right;
    const innerH = height - MARGIN.top - MARGIN.bottom;
    const max = niceMax(Math.max(...data.map((d) => Number(d.revenue))));
    const step = data.length > 1 ? innerW / (data.length - 1) : 0;
    const x = (i) => MARGIN.left + i * step;
    const y = (v) => MARGIN.top + innerH - (Number(v) / max) * innerH;
    const points = data.map((d, i) => [x(i), y(d.revenue)]);
    const line = points.map(([px, py], i) => `${i ? 'L' : 'M'}${px.toFixed(1)},${py.toFixed(1)}`).join(' ');
    const base = MARGIN.top + innerH;
    const area = points.length ? `${line} L${points.at(-1)[0].toFixed(1)},${base} L${points[0][0].toFixed(1)},${base} Z` : '';
    const ticks = Array.from({ length: TICKS + 1 }, (_, i) => (max / TICKS) * i);
    return { innerW, innerH, max, step, x, y, line, area, ticks, base };
  }, [data, width, height]);

  if (!data.length) return null;
  const { x, y, line, area, ticks, base, step } = geometry;
  const labelIdx = [...new Set([0, Math.floor((data.length - 1) / 2), data.length - 1])];

  const pick = (clientX) => {
    const rect = wrapper.current.getBoundingClientRect();
    const i = step ? Math.round((clientX - rect.left - MARGIN.left) / step) : 0;
    setActive(Math.max(0, Math.min(data.length - 1, i)));
  };
  const onKey = (e) => {
    if (e.key === 'ArrowRight') setActive((a) => Math.min(data.length - 1, (a ?? -1) + 1));
    if (e.key === 'ArrowLeft') setActive((a) => Math.max(0, (a ?? data.length) - 1));
  };

  const point = active !== null ? data[active] : null;
  const tooltipLeft = active !== null ? Math.min(Math.max(x(active), 80), width - 80) : 0;

  return (
    <div ref={wrapper} className="relative" onPointerLeave={() => setActive(null)}>
      <svg width={width} height={height} role="img" tabIndex={0} onKeyDown={onKey}
           onPointerMove={(e) => pick(e.clientX)} onBlur={() => setActive(null)}
           aria-label={`Revenue per day, ${formatShortDate(data[0].date)} to ${formatShortDate(data.at(-1).date)}. Use the arrow keys to read each day.`}
           className="block touch-none outline-none">
        {ticks.map((t) => (
          <g key={t}>
            <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(t)} y2={y(t)} stroke="#eceae6" strokeWidth="1" />
            <text x={MARGIN.left - 8} y={y(t)} dy="0.32em" textAnchor="end" className="fill-gray-500 text-[11px] tabular-nums">
              {formatPriceCompact(t)}
            </text>
          </g>
        ))}
        {labelIdx.map((i) => (
          <text key={i} x={x(i)} y={base + 18} textAnchor={i === 0 ? 'start' : i === data.length - 1 ? 'end' : 'middle'}
                className="fill-gray-500 text-[11px]">
            {formatShortDate(data[i].date)}
          </text>
        ))}
        <path d={area} fill="var(--color-brand-600)" fillOpacity="0.1" />
        <path d={line} fill="none" stroke="var(--color-brand-600)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {point && (
          <>
            <line x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={base} stroke="#c3c2b7" strokeWidth="1" />
            <circle cx={x(active)} cy={y(point.revenue)} r="4" fill="var(--color-brand-600)" stroke="#ffffff" strokeWidth="2" />
          </>
        )}
      </svg>
      {point && (
        <div role="status" className="pointer-events-none absolute top-0 -translate-x-1/2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-md"
             style={{ left: tooltipLeft }}>
          <p className="text-sm font-semibold text-gray-900 tabular-nums">{formatPrice(point.revenue)}</p>
          <p className="text-gray-500">{formatShortDate(point.date)} · {point.orders} {point.orders === 1 ? 'order' : 'orders'}</p>
        </div>
      )}
    </div>
  );
}
