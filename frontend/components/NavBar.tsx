"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Menu, X } from "lucide-react";

const LINKS = [
  { href: "/runs", label: "Runs" },
  { href: "/cases", label: "Cases" },
  { href: "/metrics", label: "Metrics" },
];

// Client nav: marks the current page with aria-current (the CSS already
// underlines it) and collapses the link cluster behind a hamburger drawer
// on narrow viewports per DESIGN.md tablet-narrow behaviour.
export default function NavBar() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  function isActive(href: string): boolean {
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  return (
    <nav className={open ? "nav nav-open" : "nav"} aria-label="Primary">
      <Link
        href="/"
        className="nav-brand"
        aria-current={pathname === "/" ? "page" : undefined}
        onClick={() => setOpen(false)}
      >
        FinRecon AI
      </Link>
      <button
        type="button"
        className="nav-toggle"
        aria-expanded={open}
        aria-controls="primary-nav-links"
        aria-label={open ? "Close navigation" : "Open navigation"}
        onClick={() => setOpen((v) => !v)}
      >
        {open ? (
          <X size={20} aria-hidden="true" />
        ) : (
          <Menu size={20} aria-hidden="true" />
        )}
      </button>
      <div className="nav-links" id="primary-nav-links">
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            aria-current={isActive(l.href) ? "page" : undefined}
            onClick={() => setOpen(false)}
          >
            {l.label}
          </Link>
        ))}
      </div>
    </nav>
  );
}
