/**
 * Decorative page background: a soft "glass" globe with a lat/long wireframe,
 * landmasses and glowing node points, plus faint brand arcs and a swoosh.
 *
 * Everything is generated from a small orthographic projection so the globe
 * looks correct (latitude bands curve, meridians converge at the poles, land
 * wraps around the visible hemisphere). Purely decorative - nothing interactive.
 */

const GLOBE = {
  size: 560,
  center: 280,
  radius: 216,
  viewLat: 20, // we look at the globe from slightly above
  viewLon: -32, // centred on the Atlantic: Americas left, Africa right
}

type LatLon = [number, number]
interface Point {
  x: number
  y: number
  visible: boolean
}
interface LineRun {
  id: string
  d: string
  emphasis: boolean
}

const toRad = (degrees: number) => (degrees * Math.PI) / 180

/** lat / lon (degrees) -> screen point, `visible` = on the near hemisphere */
function project(lat: number, lon: number): Point {
  const phi = toRad(lat)
  const lambda = toRad(lon - GLOBE.viewLon)
  const phi0 = toRad(GLOBE.viewLat)
  const cosPhi = Math.cos(phi)

  const x = cosPhi * Math.sin(lambda)
  const y = Math.cos(phi0) * Math.sin(phi) - Math.sin(phi0) * cosPhi * Math.cos(lambda)
  const z = Math.sin(phi0) * Math.sin(phi) + Math.cos(phi0) * cosPhi * Math.cos(lambda)

  return {
    x: GLOBE.center + GLOBE.radius * x,
    y: GLOBE.center - GLOBE.radius * y,
    visible: z > 0,
  }
}

/** Samples one line (a parallel or a meridian) into visible runs of points. */
function sampleLine(points: LatLon[]): Point[][] {
  const runs: Point[][] = []
  let current: Point[] = []
  points.forEach(([lat, lon]) => {
    const p = project(lat, lon)
    if (p.visible) {
      current.push(p)
    } else if (current.length) {
      runs.push(current)
      current = []
    }
  })
  if (current.length) runs.push(current)
  return runs.filter((run) => run.length > 1)
}

const toPathData = (points: Point[], close = false) => {
  const body = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
    .join(' ')
  return close ? `${body} Z` : body
}

/** Smooth closed outline for a landmass, built from its projected points. */
function toSmoothShape(points: Point[]) {
  const first = points[0]
  if (!first || points.length < 4) return ''
  let d = `M${first.x.toFixed(1)} ${first.y.toFixed(1)}`
  for (let index = 1; index < points.length; index += 1) {
    const previous = points[index - 1]
    const current = points[index]
    if (!previous || !current) continue
    const midX = (previous.x + current.x) / 2
    const midY = (previous.y + current.y) / 2
    d += ` Q${previous.x.toFixed(1)} ${previous.y.toFixed(1)} ${midX.toFixed(1)} ${midY.toFixed(1)}`
  }
  return `${d} Z`
}

/* ------------------------------------------------------------------ */
/* Latitude / longitude grid                                           */
/* ------------------------------------------------------------------ */

const PARALLELS = [-60, -30, 0, 30, 60]
const MERIDIANS = Array.from({ length: 12 }, (_, index) => index * 30)

const PARALLEL_PATHS: LineRun[] = PARALLELS.flatMap((lat) =>
  sampleLine(Array.from({ length: 181 }, (_, index): LatLon => [lat, index * 2])).map(
    (run, runIndex) => ({
      id: `p-${lat}-${runIndex}`,
      d: toPathData(run),
      emphasis: lat === 0,
    }),
  ),
)

const MERIDIAN_PATHS: LineRun[] = MERIDIANS.flatMap((lon) =>
  sampleLine(Array.from({ length: 121 }, (_, index): LatLon => [-90 + index * 1.5, lon])).map(
    (run, runIndex) => ({
      id: `m-${lon}-${runIndex}`,
      d: toPathData(run),
      emphasis: lon === 0,
    }),
  ),
)

/* ------------------------------------------------------------------ */
/* Landmasses (coarse lat/lon outlines - accurate enough for a backdrop) */
/* ------------------------------------------------------------------ */

const LANDMASSES: LatLon[][] = [
  // North America - walked clockwise from Alaska
  [
    [70, -160],
    [71, -140],
    [70, -125],
    [72, -105],
    [70, -90],
    [66, -82],
    [62, -75],
    [58, -66],
    [52, -58],
    [47, -60],
    [44, -66],
    [42, -70],
    [38, -74],
    [34, -77],
    [31, -81],
    [26, -80],
    [25, -76],
    [22, -84],
    [20, -90],
    [19, -96],
    [16, -98],
    [20, -105],
    [26, -112],
    [30, -116],
    [34, -120],
    [40, -124],
    [46, -124],
    [50, -128],
    [54, -133],
    [57, -140],
    [59, -150],
    [61, -163],
    [65, -168],
    [70, -160],
  ],
  // South America - west coast north, east coast south
  [
    [-55, -68],
    [-52, -74],
    [-48, -75],
    [-45, -74],
    [-42, -74],
    [-38, -73],
    [-33, -72],
    [-28, -71],
    [-23, -70],
    [-18, -70],
    [-14, -76],
    [-10, -78],
    [-6, -81],
    [-2, -80],
    [1, -79],
    [4, -77],
    [8, -77],
    [11, -75],
    [12, -72],
    [10, -64],
    [8, -60],
    [5, -52],
    [1, -50],
    [-3, -44],
    [-6, -36],
    [-9, -35],
    [-13, -38],
    [-16, -39],
    [-19, -40],
    [-22, -41],
    [-25, -48],
    [-29, -50],
    [-32, -52],
    [-35, -57],
    [-38, -58],
    [-41, -63],
    [-45, -66],
    [-48, -68],
    [-52, -69],
    [-55, -68],
  ],
  // Africa - clockwise from Morocco
  [
    [36, -6],
    [35, 0],
    [37, 5],
    [37, 10],
    [33, 11],
    [32, 20],
    [31, 25],
    [31, 32],
    [30, 33],
    [24, 35],
    [20, 37],
    [16, 39],
    [13, 43],
    [11, 44],
    [11, 51],
    [5, 48],
    [1, 42],
    [-4, 40],
    [-9, 40],
    [-13, 40],
    [-17, 38],
    [-20, 35],
    [-23, 34],
    [-26, 33],
    [-30, 31],
    [-33, 29],
    [-35, 26],
    [-34, 22],
    [-34, 19],
    [-31, 17],
    [-28, 16],
    [-25, 15],
    [-22, 14],
    [-18, 12],
    [-14, 12],
    [-11, 13],
    [-8, 12],
    [-5, 11],
    [-2, 10],
    [0, 9],
    [3, 7],
    [4, 3],
    [5, -4],
    [7, -9],
    [9, -13],
    [11, -16],
    [15, -16],
    [18, -16],
    [21, -17],
    [24, -14],
    [27, -12],
    [30, -9],
    [33, -6],
    [36, -6],
  ],
  // Europe + western Asia (the part facing us; the rest is over the horizon)
  [
    [36, -9],
    [43, -9],
    [48, -5],
    [51, 2],
    [54, 9],
    [58, 8],
    [62, 12],
    [66, 15],
    [70, 20],
    [70, 30],
    [68, 40],
    [64, 50],
    [60, 58],
    [55, 60],
    [50, 58],
    [46, 55],
    [42, 52],
    [40, 47],
    [42, 42],
    [40, 36],
    [36, 36],
    [36, 28],
    [40, 26],
    [44, 25],
    [42, 20],
    [40, 15],
    [38, 10],
    [36, -9],
  ],
  // Australia - only its edge peeks around the curve
  [
    [-12, 132],
    [-14, 142],
    [-20, 149],
    [-28, 153],
    [-35, 150],
    [-38, 144],
    [-35, 137],
    [-32, 130],
    [-25, 114],
    [-18, 122],
    [-12, 132],
  ],
]

const LAND_PATHS = LANDMASSES.flatMap((polygon, landIndex) =>
  sampleLine(polygon)
    .map((run) => toSmoothShape(run))
    .filter(Boolean)
    .map((d, runIndex) => ({ id: `land-${landIndex}-${runIndex}`, d })),
)

/* ------------------------------------------------------------------ */
/* Node points where the grid crosses                                  */
/* ------------------------------------------------------------------ */

const NODES = PARALLELS.flatMap((lat, latIndex) =>
  MERIDIANS.flatMap((lon, lonIndex) => {
    // one node every other crossing keeps the grid from looking busy
    if ((latIndex + lonIndex) % 2 !== 0) return []

    const point = project(lat, lon)
    if (!point.visible) return []

    return [{ x: point.x, y: point.y, emphasised: (latIndex + lonIndex) % 4 === 0 }]
  }),
)

export function BackgroundDecoration() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-[#eef4fc] via-[#f8fbfe] to-[#e7eefa]" />

      {/* the globe, cropped by the left edge of the screen */}
      <svg
        viewBox={`0 0 ${GLOBE.size} ${GLOBE.size}`}
        fill="none"
        className="absolute top-1/2 -left-[14%] h-[min(80vh,780px)] w-auto -translate-y-[52%]"
      >
        <defs>
          <radialGradient id="gssSphere" cx="34%" cy="26%" r="78%">
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="62%" stopColor="#eaf2fd" />
            <stop offset="100%" stopColor="#d3e2f7" />
          </radialGradient>
          <radialGradient id="gssHalo" cx="50%" cy="50%" r="50%">
            <stop offset="60%" stopColor="#8FB4E8" stopOpacity="0.28" />
            <stop offset="100%" stopColor="#8FB4E8" stopOpacity="0" />
          </radialGradient>
          <clipPath id="gssSphereClip">
            <circle cx={GLOBE.center} cy={GLOBE.center} r={GLOBE.radius} />
          </clipPath>
        </defs>

        {/* atmosphere glow behind the sphere */}
        <circle cx={GLOBE.center} cy={GLOBE.center} r={GLOBE.radius * 1.32} fill="url(#gssHalo)" />

        {/* sphere body */}
        <circle cx={GLOBE.center} cy={GLOBE.center} r={GLOBE.radius} fill="url(#gssSphere)" />

        <g clipPath="url(#gssSphereClip)">
          {/* landmasses */}
          {LAND_PATHS.map((land) => (
            <path
              key={land.id}
              d={land.d}
              fill="#ffffff"
              fillOpacity="0.8"
              stroke="#C9DAF3"
              strokeWidth="0.9"
            />
          ))}

          {/* latitude + longitude wireframe */}
          {PARALLEL_PATHS.map((line) => (
            <path
              key={line.id}
              d={line.d}
              stroke={line.emphasis ? '#A5C0E7' : '#BCCFEE'}
              strokeWidth={line.emphasis ? 1.1 : 0.85}
              opacity={line.emphasis ? 0.85 : 0.62}
            />
          ))}
          {MERIDIAN_PATHS.map((line) => (
            <path
              key={line.id}
              d={line.d}
              stroke={line.emphasis ? '#A5C0E7' : '#BCCFEE'}
              strokeWidth={line.emphasis ? 1.1 : 0.85}
              opacity={line.emphasis ? 0.85 : 0.62}
            />
          ))}

          {/* glowing connections where the grid crosses */}
          {NODES.map((node, index) => (
            <g key={index}>
              {node.emphasised && (
                <circle cx={node.x} cy={node.y} r="8" fill="#ffffff" fillOpacity="0.55" />
              )}
              <circle
                cx={node.x}
                cy={node.y}
                r={node.emphasised ? 3 : 1.9}
                fill="#ffffff"
                fillOpacity={node.emphasised ? 0.98 : 0.75}
              />
            </g>
          ))}
        </g>

        {/* crisp limb + inner rim */}
        <circle
          cx={GLOBE.center}
          cy={GLOBE.center}
          r={GLOBE.radius}
          stroke="#DCE7F7"
          strokeWidth="1.5"
        />
        <circle
          cx={GLOBE.center}
          cy={GLOBE.center}
          r={GLOBE.radius - 1}
          stroke="#ffffff"
          strokeWidth="1"
          opacity="0.8"
        />
      </svg>

      {/* faint orbit arcs sweeping across the right side */}
      <svg
        viewBox="0 0 700 900"
        fill="none"
        className="absolute top-[4%] -right-[7%] h-[92vh] w-auto"
      >
        <path
          d="M-40 300C160 340 340 430 700 780"
          stroke="#E0242B"
          strokeWidth="1.6"
          opacity="0.3"
          strokeLinecap="round"
        />
        <path
          d="M10 170C210 225 400 330 700 640"
          stroke="#1B3A93"
          strokeWidth="1.4"
          opacity="0.22"
          strokeLinecap="round"
        />
        <path
          d="M180 60C360 120 540 250 700 420"
          stroke="#9DB6E4"
          strokeWidth="1.3"
          opacity="0.4"
          strokeLinecap="round"
        />
        <circle cx="452" cy="248" r="4.5" fill="#9DB6E4" opacity="0.7" />
        <circle cx="646" cy="497" r="4" fill="#9DB6E4" opacity="0.6" />
        <circle cx="196" cy="72" r="3.5" fill="#E0242B" opacity="0.45" />
      </svg>

      {/* brand swoosh - bottom right */}
      <svg
        viewBox="0 0 720 420"
        fill="none"
        className="absolute -right-24 -bottom-24 h-[32vh] max-h-[290px] w-auto min-w-[520px]"
      >
        <path d="M0 420C210 300 470 250 720 60v360H0Z" fill="#1B3A93" opacity="0.9" />
        <path
          d="M0 420C200 320 430 268 720 100"
          stroke="#D32027"
          strokeWidth="11"
          strokeLinecap="round"
        />
        <path
          d="M0 420C215 318 480 262 720 88"
          stroke="#ffffff"
          strokeWidth="2"
          strokeLinecap="round"
          opacity="0.35"
        />
      </svg>
    </div>
  )
}
