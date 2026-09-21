import React, { useState, useRef, useEffect } from "react";
import { ChevronDown, ChevronLeft, ChevronRight, Calendar as CalendarIcon } from "lucide-react";
import { WorkoutDay } from "../types";

interface Props {
  value: string; // YYYY-MM-DD
  onChange: (date: string) => void;
  dataFrame: WorkoutDay[];
}

export function CustomCalendarDropdown({ value, onChange, dataFrame }: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Use the value to set current month view, default to today if not provided
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
    // A record exists if it's in the dataFrame and has actual values logged (not just empty arrays or 0 total if that implies no data)
    // Here we'll just check if it exists in dataFrame.
    return dataFrame.some(d => d.date === dateString);
  };

  const monthNames = ["January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"];
  
  return (
    <div className="relative flex-1 min-w-0" ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="w-full h-[42px] flex items-center justify-center bg-white dark:bg-slate-950 border border-slate-300 dark:border-white/5 rounded-xl px-3 text-xs text-slate-900 dark:text-white focus:outline-none focus:border-brand-primary font-mono cursor-pointer transition-colors"
      >
        <span className="flex items-center gap-2">
          {value || "Select Date"}
        </span>
      </button>

      {isOpen && (
        <div className="absolute top-full left-1/2 -translate-x-1/2 mt-2 w-64 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-800 rounded-xl shadow-2xl p-3 z-50">
          <div className="flex justify-between items-center mb-3">
            <button type="button" onClick={prevMonth} className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-colors text-slate-700 dark:text-slate-300 cursor-pointer">
              <ChevronLeft size={16} />
            </button>
            <div className="text-sm font-bold text-slate-800 dark:text-white font-mono">
              {monthNames[currentMonth.getMonth()]} {currentMonth.getFullYear()}
            </div>
            <button type="button" onClick={nextMonth} className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-colors text-slate-700 dark:text-slate-300 cursor-pointer">
              <ChevronRight size={16} />
            </button>
          </div>
          
          <div className="grid grid-cols-7 gap-1 mb-1">
            {["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map(day => (
              <div key={day} className="text-[10px] text-center text-slate-500 font-bold font-mono py-1">
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
                    relative flex flex-col items-center justify-center p-1.5 h-9 rounded-lg text-xs font-mono transition-colors cursor-pointer
                    ${isSelected 
                      ? "bg-brand-primary text-white font-bold" 
                      : "text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800"}
                  `}
                >
                  <span className="mb-[2px]">{day}</span>
                  {/* Indicator dots container */}
                  <div className="absolute bottom-[2px] flex space-x-[2px]">
                    {hasData && (
                      <>
                        <div className={`w-[2.5px] h-[2.5px] rounded-full ${isSelected ? 'bg-white' : 'bg-brand-primary'}`} />
                        <div className={`w-[2.5px] h-[2.5px] rounded-full ${isSelected ? 'bg-white' : 'bg-brand-primary'}`} />
                        <div className={`w-[2.5px] h-[2.5px] rounded-full ${isSelected ? 'bg-white' : 'bg-brand-primary'}`} />
                      </>
                    )}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
