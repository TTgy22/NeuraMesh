import { useEffect, useRef } from "react";

// 进场动画：将 "NeuraMesh" 文本采样为像素点，从随机位置汇聚成字，再淡出进入主界面。
export function PixelIntro({ onDone }: { onDone: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const done = useRef(false);

  function finish() {
    if (!done.current) {
      done.current = true;
      onDone();
    }
  }

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return finish();
    const ctx = canvas.getContext("2d");
    if (!ctx) return finish();

    const W = (canvas.width = window.innerWidth);
    const H = (canvas.height = window.innerHeight);

    const off = document.createElement("canvas");
    off.width = W; off.height = H;
    const octx = off.getContext("2d");
    if (!octx) return finish();
    octx.fillStyle = "#ffffff";
    octx.textAlign = "center";
    octx.textBaseline = "middle";
    const fontSize = Math.max(40, Math.min(150, Math.floor(W / 7)));
    octx.font = `700 ${fontSize}px "Space Grotesk", sans-serif`;
    octx.fillText("NeuraMesh", W / 2, H / 2);
    const data = octx.getImageData(0, 0, W, H).data;

    const step = 7;
    const pts: { tx: number; ty: number; sx: number; sy: number }[] = [];
    for (let y = 0; y < H; y += step) {
      for (let x = 0; x < W; x += step) {
        if (data[(y * W + x) * 4 + 3] > 128) {
          pts.push({ tx: x, ty: y, sx: Math.random() * W, sy: Math.random() * H });
        }
      }
    }

    const SETTLE = 1300, HOLD = 700, FADE = 500;
    const start = performance.now();
    let raf = 0;
    const size = step - 1;

    const loop = (now: number) => {
      const t = now - start;
      const p = Math.min(1, t / SETTLE);
      const ease = 1 - Math.pow(1 - p, 3);
      let alpha = 1;
      if (t > SETTLE + HOLD) alpha = Math.max(0, 1 - (t - SETTLE - HOLD) / FADE);
      ctx.clearRect(0, 0, W, H);
      ctx.globalAlpha = alpha;
      for (const pt of pts) {
        const cx = pt.sx + (pt.tx - pt.sx) * ease;
        const cy = pt.sy + (pt.ty - pt.sy) * ease;
        ctx.fillStyle = "rgb(120, 205, 215)";
        ctx.fillRect(cx, cy, size, size);
      }
      ctx.globalAlpha = 1;
      if (t > SETTLE + HOLD + FADE) return finish();
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);

    const timeout = window.setTimeout(finish, SETTLE + HOLD + FADE + 800);
    return () => { cancelAnimationFrame(raf); clearTimeout(timeout); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div onClick={finish}
      style={{ position: "fixed", inset: 0, background: "var(--bg)", zIndex: 9999, cursor: "pointer" }}>
      <canvas ref={canvasRef} style={{ display: "block" }} />
      <div className="mono" style={{ position: "absolute", bottom: 28, width: "100%",
        textAlign: "center", color: "var(--muted)", fontSize: 12, letterSpacing: 2 }}>
        点击跳过 · DECENTRALIZED EDGE COMPUTE
      </div>
    </div>
  );
}