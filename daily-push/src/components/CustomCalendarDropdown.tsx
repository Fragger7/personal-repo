import React, { useState, useRef, useEffect } from "react";
import { createPortal } from "react-dom";
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon } from "lucide-react";
import { WorkoutDay } from "../types";

interface Props {
  value: string; // YYYY-MM-DD
  onChange: (date: string) => void;
  dataFrame: WorkoutDay[];
}

export function CustomCalendarDropdown({ value, onChange, dataFrame }: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const [coords, setCoords] = useState({ top: 0, left: 0 });

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node;
      if (
        dropdownRef.current && !dropdownRef.current.contains(target) &&
        popoverRef.current && !popoverRef.current.contains(target)
      ) {
        setIsOpen(false);
      }
    }
    
    function handleScroll() {
      if (isOpen) updateCoords();
    }
    
    document.addEventListener("mousedown", handleClickOutside);
    window.addEventListener("scroll", handleScroll, true);
    window.addEventListener("resize", handleScroll);
    
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      window.removeEventListener("scroll", handleScroll, true);
      window.removeEventListener("resize", handleScroll);
    };
  }, [isOpen]);

  const updateCoords = () => {
    if (dropdownRef.current) {
      const rect = dropdownRef.current.getBoundingClientRect();
      const popoverWidth = 288; // w-72 = 18rem = 288px
      let left = rect.left + window.scrollX + (rect.width / 2) - (popoverWidth / 2);
      
      // Keep it within screen bounds
      if (left < 16) left = 16;
      if (left + popoverWidth > window.innerWidth - 16) left = window.innerWidth - popoverWidth - 16;
      
      setCoords({
        top: rect.bottom + window.scrollY + 8,
        left: left,
      });
    }
  };

  const toggleOpen = () => {
    if (!isOpen) {
      updateCoords();
    }
    setIsOpen(!isOpen);
  };

  const parsedDate = value ? new Date(value + "T00:00:00") : new Date();
  const [currentMonth, setCurrentMonth] = useState(new Date(parsedDate.getFullYear(), parsedDate.getMonth(), 1));

  const prevMonth = () => {
    setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1));
  };
  const nextMonth = () => {
    setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1));
  };

  const daysInMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 0).getDate();
  const firstDayOfMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), 1).getDay();

  const handleDateClick = (day: number) => {
    const selectedDate = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), day);
    const dateString = `${selectedDate.getFullYear()}-${String(selectedDate.getMonth() + 1).padStart(2, "0")}-${String(selectedDate.getDate()).padStart(2, "0")}`;
    onChange(dateString);
    setIsOpen(false);
  };

  const hasRecord = (day: number) => {
    const dateString = `${currentMonth.getFullYear()}-${String(currentMonth.getMonth() + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    const record = dataFrame.find(d => d.date === dateString);
    if (!record) return false;
    
    // Check if there is actual mutated data (sum > 0)
    const pSum = record.p.reduce((acc, val) => acc + (val || 0), 0);
    const cSum = record.c.reduce((acc, val) => acc + (val || 0), 0);
    return (pSum > 0 || cSum > 0);
  };

  const monthNames = ["January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"];
  
  const popoverContent = isOpen ? (
    <div 
      ref={popoverRef}
      style={{ top: `${coords.top}px`, left: `${coords.left}px` }}
      className="absolute w-72 bg-white/90 dark:bg-card-dark/90 backdrop-blur-3xl border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl shadow-slate-200/50 dark:shadow-black/60 p-4 z-[9999]"
    >
      <div className="flex justify-between items-center mb-4">
        <button type="button" onClick={prevMonth} className="p-1.5 hover:bg-slate-200/50 dark:hover:bg-white/10 rounded-lg transition-colors text-slate-700 dark:text-slate-300 cursor-pointer">
          <ChevronLeft size={18} />
        </button>
        <div className="text-[13px] font-bold text-slate-800 dark:text-white font-mono uppercase tracking-widest">
          {monthNames[currentMonth.getMonth()]} {currentMonth.getFullYear()}
        </div>
        <button type="button" onClick={nextMonth} className="p-1.5 hover:bg-slate-200/50 dark:hover:bg-white/10 rounded-lg transition-colors text-slate-700 dark:text-slate-300 cursor-pointer">
          <ChevronRight size={18} />
        </button>
      </div>
      
      <div className="grid grid-cols-7 gap-1 mb-2">
        {["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map(day => (
          <div key={day} className="text-[10px] text-center text-slate-500 font-bold font-mono py-1 tracking-widest">
            {day}
          </div>
        ))}
      </div>
      
      <div className="grid grid-cols-7 gap-1">
        {Array.from({ length: firstDayOfMonth }).map((_, i) => (
          <div key={`empty-${i}`} className="p-2" />
        ))}
        
        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = i + 1;
          const dateString = `${currentMonth.getFullYear()}-${String(currentMonth.getMonth() + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
          const isSelected = value === dateString;
          const hasData = hasRecord(day);
          
          return (
            <button
              key={day}
              type="button"
              onClick={() => handleDateClick(day)}
              className={`
                relative flex flex-col items-center justify-center h-9 w-full rounded-xl text-xs font-mono transition-all cursor-pointer border
                ${isSelected 
                  ? "bg-brand-primary border-brand-primary text-white font-bold shadow-lg shadow-brand-primary/30" 
                  : "bg-transparent border-transparent text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5"}
              `}
            >
              <span className="mb-[2px]">{day}</span>
              {/* Indicator dots container */}
              <div className="absolute bottom-[4px] flex space-x-[2px]">
                {hasData && (
                  <>
                    <div className={`w-[3px] h-[3px] rounded-full ${isSelected ? 'bg-white' : 'bg-brand-accent dark:bg-brand-accent'}`} />
                    <div className={`w-[3px] h-[3px] rounded-full ${isSelected ? 'bg-white' : 'bg-brand-accent dark:bg-brand-accent'}`} />
                    <div className={`w-[3px] h-[3px] rounded-full ${isSelected ? 'bg-white' : 'bg-brand-accent dark:bg-brand-accent'}`} />
                  </>
                )}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  ) : null;

  return (
    <div className="relative flex-1 min-w-0" ref={dropdownRef}>
      <button
        type="button"
        onClick={toggleOpen}
        className="w-full h-full min-h-[46px] flex items-center justify-center bg-white dark:bg-slate-950 border border-slate-300 dark:border-white/5 rounded-xl px-3 text-xs text-slate-900 dark:text-white focus:outline-none focus:border-brand-primary hover:border-brand-primary/50 font-mono cursor-pointer transition-colors"
      >
        <span className="flex items-center gap-2 tracking-wider">
          <CalendarIcon size={14} className="text-slate-500" />
          {value || "Select Date"}
        </span>
      </button>
      
      {isOpen && typeof document !== "undefined" && createPortal(popoverContent, document.body)}
    </div>
  );
}
