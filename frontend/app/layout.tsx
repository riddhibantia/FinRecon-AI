import "./globals.css";

export const metadata = {
  title: "FinRecon AI — Analyst Dashboard",
  description: "Reconciliation exception queue, case evidence, and cited AI investigations.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <nav className="nav">
          <a href="/">FinRecon AI</a>
          <a href="/runs">Runs</a>
          <a href="/cases">Cases</a>
        </nav>
        <main className="page">{children}</main>
      </body>
    </html>
  );
}
