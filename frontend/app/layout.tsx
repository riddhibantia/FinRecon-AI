import type { Metadata, Viewport } from "next";
import "./globals.css";
import NavBar from "@/components/NavBar";

export const metadata: Metadata = {
  metadataBase: new URL("http://localhost:3000"),
  title: {
    default: "FinRecon AI — Analyst Dashboard",
    template: "%s · FinRecon AI",
  },
  description: "Reconciliation exception queue, case evidence, and cited AI investigations.",
  robots: { index: false, follow: false },
  openGraph: {
    title: "FinRecon AI — Analyst Dashboard",
    description: "Reconciliation exception queue, case evidence, and cited AI investigations.",
    type: "website",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#111111",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <a href="#main" className="skip-link">
          Skip to main content
        </a>
        <div className="utility-bar" aria-label="Workspace status">
          <span>Analyst workspace</span>
          <span>Policy-gated AI</span>
          <span>Human review required</span>
        </div>
        <NavBar />
        <main id="main" className="page" tabIndex={-1}>
          {children}
        </main>
        <footer className="footer">
          FinRecon AI · Facts from backends only — never guesses · AI ends at human review
        </footer>
      </body>
    </html>
  );
}
