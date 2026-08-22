import type { Metadata } from "next";
import { Inter, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth-context";
import MotionPrefInit from "@/components/MotionPrefInit";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const plexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "PrepPanel — Interview practice that actually pushes back",
  description:
    "PrepPanel runs realistic, role-specific mock interviews powered by real LLMs, scores every answer, and researches the company you're actually interviewing with.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${inter.variable} ${plexMono.variable}`}>
      <body className="bg-canvas font-sans antialiased">
        <MotionPrefInit />
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
