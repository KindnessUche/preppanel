import Nav from "@/components/landing/Nav";
import Hero from "@/components/landing/Hero";
import Principles from "@/components/landing/Principles";
import ChapterSection from "@/components/landing/ChapterSection";
import ChapterIndex from "@/components/landing/ChapterIndex";
import PanelPersonas from "@/components/landing/panels/PanelPersonas";
import PanelPractice from "@/components/landing/panels/PanelPractice";
import PanelScore from "@/components/landing/panels/PanelScore";
import PanelResearch from "@/components/landing/panels/PanelResearch";
import PanelProgress from "@/components/landing/panels/PanelProgress";
import StackBand from "@/components/landing/StackBand";
import Roadmap from "@/components/landing/Roadmap";
import CTA from "@/components/landing/CTA";
import Footer from "@/components/landing/Footer";
import { chapters } from "@/lib/content";

export default function Home() {
  return (
    <main className="relative">
      <Nav />
      <ChapterIndex />
      <Hero />
      <Principles />

      <ChapterSection chapter={chapters[0]} layout="split">
        <PanelPersonas />
      </ChapterSection>

      <ChapterSection chapter={chapters[1]} layout="stacked">
        <PanelPractice />
      </ChapterSection>

      <ChapterSection chapter={chapters[2]} layout="split" reverse>
        <PanelScore />
      </ChapterSection>

      <ChapterSection chapter={chapters[3]} layout="stacked">
        <PanelResearch />
      </ChapterSection>

      <ChapterSection chapter={chapters[4]} layout="split">
        <PanelProgress />
      </ChapterSection>

      <StackBand />
      <Roadmap />
      <CTA />
      <Footer />
    </main>
  );
}
