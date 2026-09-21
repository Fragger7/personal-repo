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

  useEffect(() => {
    if (isOpen) {
      setCurrentMonth(getInitialMonth());
    }
  }, [isOpen, value]);

  const updateCoords = () => {
    if (dropdownRef.current) {
      const rect = dropdownRef.current.getBoundingClientRect();
      const popoverWidth = 300; 
      let left = rect.left + window.scrollX + (rect.width / 2) - (popoverWidth / 2);
      
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

  const getInitialMonth = () => {
    if (!value) return new Date();
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, 1);
  };

  const [currentMonth, setCurrentMonth] = useState<Date>(getInitialMonth());

  const prevMonth = () => {
    setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1));
  };
  const nextMonth = () => {
    setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1));
  };

  const daysInMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 0).getDate();
  const firstDayOfMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), 1).getDay();

  const handleDateClick = (day: number) => {
    const yyyy = currentMonth.getFullYear();
    const mm = String(currentMonth.getMonth() + 1).padStart(2, "0");
    const dd = String(day).padStart(2, "0");
    onChange(`${yyyy}-${mm}-${dd}`);
    setIsOpen(false);
  };

  const hasRecord = (day: number) => {
    const yyyy = currentMonth.getFullYear();
    const mm = String(currentMonth.getMonth() + 1).padStart(2, "0");
    const dd = String(day).padStart(2, "0");
    const dateString = `${yyyy}-${mm}-${dd}`;
    
    // Check if the record exists and has at least one explicit number logged (even if it is 0)
    return dataFrame.some(d => {
      if (d.date !== dateString) return false;
      const hasAnyValue = [...d.p, ...d.c].some(val => val !== null);
      return hasAnyValue;
    });
  };

  const monthNames = ["January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"];
  
  const popoverContent = isOpen ? (
    <div 
      ref={popoverRef}
      style={{ top: `${coords.top}px`, left: `${coords.left}px`, width: '300px' }}
      className="absolute bg-white/95 dark:bg-slate-900/95 backdrop-blur-3xl border border-slate-200 dark:border-white/10 rounded-2xl shadow-2xl p-4 z-[9999]"
    >
      <div className="flex justify-between items-center mb-4">
        <button type="button" onClick={prevMonth} className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-colors text-slate-700 dark:text-slate-300">
          <ChevronLeft size={16} />
        </button>
        <div className="text-xs font-bold text-slate-800 dark:text-white font-mono uppercase tracking-widest">
          {monthNames[currentMonth.getMonth()]} {currentMonth.getFullYear()}
        </div>
        <button type="button" onClick={nextMonth} className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-colors text-slate-700 dark:text-slate-300">
          <ChevronRight size={16} />
        </button>
      </div>
      
      <div className="grid grid-cols-7 gap-1 mb-2">
        {["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map(day => (
          <div key={day} className="text-[11px] text-center text-slate-400 font-bold font-mono py-1">
            {day}
          </div>
        ))}
      </div>
      
      <div className="grid grid-cols-7 gap-1">
        {Array.from({ length: firstDayOfMonth }).map((_, i) => (
          <div key={`empty-${i}`} className="p-1" />
        ))}
        
        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = i + 1;
          const yyyy = currentMonth.getFullYear();
          const mm = String(currentMonth.getMonth() + 1).padStart(2, "0");
          const dd = String(day).padStart(2, "0");
          const dateString = `${yyyy}-${mm}-${dd}`;
          
          const isSelected = value === dateString;
          const hasData = hasRecord(day);
          
          return (
            <button
              key={day}
              type="button"
              onClick={() => handleDateClick(day)}
              className={`
                flex flex-col items-center justify-center h-10 rounded-lg text-[13px] font-mono transition-colors cursor-pointer border
                ${isSelected 
                  ? "bg-brand-primary border-brand-primary text-white font-bold shadow-md" 
                  : "bg-transparent border-transparent text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800"}
              `}
            >
              <span>{day}</span>
              <div className={`flex space-x-[3px] mt-[3px] ${hasData ? 'opacity-100' : 'opacity-0'}`}>
                <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: isSelected ? '#ffffff' : '#06b6d4' }} />
                <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: isSelected ? '#ffffff' : '#06b6d4' }} />
                <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: isSelected ? '#ffffff' : '#06b6d4' }} />
              </div>
            </button>
          );
        })}
      </div>
    </div>
  ) : null;

  return (
    <div className="relative flex-1" ref={dropdownRef}>
      <button
        type="button"
        onClick={toggleOpen}
        className="w-full bg-white dark:bg-slate-950 border border-slate-300 dark:border-white/5 rounded-xl p-3 text-xs text-slate-900 dark:text-white flex items-center justify-center focus:outline-none focus:border-brand-primary font-mono cursor-pointer transition-colors"
      >
        <span className="flex items-center gap-2">
          {value || "Select Date"}
        </span>
      </button>
      
      {isOpen && typeof document !== "undefined" && createPortal(popoverContent, document.body)}
    </div>
  );
}
