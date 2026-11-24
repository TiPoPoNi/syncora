package com.prs.scheduler;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class WorkingDayCalculator {
    
    public LocalDate calculateFinishDate(LocalDate startDate, int durationWorkingDays) {
        if (durationWorkingDays <= 0) return startDate;
        
        LocalDate currentDate = getEffectiveStartDate(startDate);
        int daysAdded = 1;
        
        while (daysAdded < durationWorkingDays) {
            currentDate = currentDate.plusDays(1);
            if (isWorkingDay(currentDate)) {
                daysAdded++;
            }
        }
        
        return currentDate;
    }

    public LocalDate getEffectiveStartDate(LocalDate date) {
        if (date == null) return LocalDate.now();
        if (isWorkingDay(date)) return date;
        return getNextWorkingDay(date);
    }
    
    public LocalDate getNextWorkingDay(LocalDate date) {
        LocalDate nextDate = date.plusDays(1);
        while (!isWorkingDay(nextDate)) {
            nextDate = nextDate.plusDays(1);
        }
        return nextDate;
    }

    public LocalDate addWorkingDays(LocalDate startDate, int workingDays) {
        LocalDate current = getEffectiveStartDate(startDate);
        for (int i = 0; i < workingDays; i++) {
            current = getNextWorkingDay(current);
        }
        return current;
    }

    public int calculateWorkingDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        if (start.isEqual(end)) return 0;

        boolean negative = start.isAfter(end);
        LocalDate from = negative ? end : start;
        LocalDate to = negative ? start : end;

        int count = 0;
        LocalDate curr = from;
        while (curr.isBefore(to)) {
            curr = curr.plusDays(1);
            if (isWorkingDay(curr)) {
                count++;
            }
        }

        return negative ? -count : count;
    }
    
    public boolean isWorkingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
