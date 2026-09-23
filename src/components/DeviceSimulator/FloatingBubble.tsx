import React, { useState } from 'react';
import { AgentState } from '../../types/agent';

interface FloatingBubbleProps {
  state: AgentState;
  onClick: () => void;
  isOpen: boolean;
}

export const FloatingBubble: React.FC<FloatingBubbleProps> = ({ state, onClick, isOpen }) => {
  const [position, setPosition] = useState<{ y: number }>({ y: 320 });
  const [isDragging, setIsDragging] = useState(false);
  const [startY, setStartY] = useState(0);

  const handlePointerDown = (e: React.PointerEvent) => {
    setIsDragging(false);
    setStartY(e.clientY);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (e.buttons === 1) {
      const delta = Math.abs(e.clientY - startY);
      if (delta > 5) {
        setIsDragging(true);
        setPosition((prev) => ({
          y: Math.max(80, Math.min(620, prev.y + (e.clientY - startY))),
        }));
        setStartY(e.clientY);
      }
    }
  };

  const handlePointerUp = () => {
    if (!isDragging) {
      onClick();
    }
    setIsDragging(false);
  };

  const getBubbleStyle = () => {
    switch (state) {
      case 'USER_INTERVENTION_REQUIRED':
        return 'bg-rose-500 shadow-rose-500/50 ring-4 ring-rose-400/40 animate-bounce';
      case 'SCANNING':
      case 'READING_QUESTION':
      case 'UNDERSTANDING_QUESTION':
      case 'SEARCHING_PROFILE':
      case 'GENERATING_RESPONSE':
        return 'bg-cyan-500 shadow-cyan-500/50 ring-4 ring-cyan-400/40 animate-pulse';
      case 'FILLING_FIELD':
      case 'NEXT_PAGE':
        return 'bg-emerald-500 shadow-emerald-500/50 ring-4 ring-emerald-400/40';
      case 'RESEARCH_COMPLETED':
        return 'bg-purple-500 shadow-purple-500/50 ring-4 ring-purple-400/40';
      default:
        return 'bg-emerald-500 shadow-emerald-500/40 hover:scale-105';
    }
  };

  return (
    <div
      style={{ top: `${position.y}px`, right: '12px' }}
      className="absolute z-30 select-none cursor-pointer touch-none"
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      title="Bolha Flutuante do Research Agent (Toque para abrir painel)"
    >
      <div
        className={`w-12 h-12 rounded-full flex items-center justify-center text-white shadow-xl transition-transform ${getBubbleStyle()} ${
          isOpen ? 'scale-110 ring-2 ring-white' : ''
        }`}
      >
        <span className="text-xl font-black">●</span>
      </div>

      {state === 'USER_INTERVENTION_REQUIRED' && (
        <span className="absolute -top-1 -right-1 flex h-4 w-4">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
          <span className="relative inline-flex rounded-full h-4 w-4 bg-rose-600 text-[9px] font-bold text-white items-center justify-center">
            !
          </span>
        </span>
      )}
    </div>
  );
};
