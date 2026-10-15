package com.meiyetec.service;
import com.meiyetec.entity.Booking;
import java.time.LocalDateTime;
import java.util.List;
/**
 * 房间+技师双资源排程核心服务 
*/
public interface ResourceScheduleService {    
/**    
 * 冲突检测结果     
*/    class ConflictCheckResult {        
public boolean hasRoomConflict;        
public boolean hasEmployeeConflict;        
public boolean hasDualEmployeeConflict;        
public List<Booking> roomConflicts;        
public List<Booking> employeeConflicts;        
public boolean hasConflict;        
public String message;    }    
/**    
 * 检测排程冲突     
*/    ConflictCheckResult checkConflict(Long roomId, Long employeeId,                                       LocalDateTime startTime, LocalDateTime endTime,                                       Long excludeBookingId);    
/**    
 * 智能排程建议    
 * 根据服务规则推荐可用房间+技师组合     
*/    List<ScheduleSuggestion> suggest(Long serviceId, LocalDateTime startTime, int durationMinutes);    
/**    
 * 排程建议项     
*/    class ScheduleSuggestion {        public Long roomId;        
public String roomName;        public Long primaryEmployeeId;        
public String primaryEmployeeName;        public Long assistantEmployeeId;        
public String assistantEmployeeName;        public Integer score;        
public String reason;    }    
/**    
 * 创建排程(包含冲突解决)     
*/    Booking createSchedule(Booking booking, String conflictResolveAction);}
