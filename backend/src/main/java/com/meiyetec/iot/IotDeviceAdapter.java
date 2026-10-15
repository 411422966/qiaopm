package com.meiyetec.iot;
import com.meiyetec.entity.IotDevice;
import com.meiyetec.entity.IotDeviceData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.Map;
/**
 * IoT 设备协议抽象
 * 统一封装 MQTT/HTTP/Modbus/BLE 等协议的数据采集与指令下发 
*/
public interface IotDeviceAdapter {    
/**    
 * 协议编码     
*/    String protocolCode();    
/**    
 * 验证设备凭证     
*/    boolean validateCredentials(IotDevice device, Map<String, String> credentials);    
/**    
 * 解析设备上报数据     
*/    ParsedDeviceReport parseReport(IotDevice device, String rawPayload);    
/**    
 * 下发指令(锁门/调整LED/启动仪器)     
*/    CommandResponse sendCommand(IotDevice device, DeviceCommand command);    
/**    
 * 设备心跳/健康检查     
*/    HealthInfo healthCheck(IotDevice device);    
/**    
 * 解析后的上报数据     
*/    
@Data    class ParsedDeviceReport {        
public boolean valid;        
public String dataType;        
// detection/treatment/operation/error/heartbeat        
public Map<String, BigDecimal> metrics;        
public String imageUrl;        
public String reportUrl;        
public String errorMessage;        public java.time.LocalDateTime deviceReportTime;    }    
/**    
 * 设备指令     
*/    
@Data    class DeviceCommand {        
public String commandType;     
// lock/unlock/adjust/start/stop        
public Map<String, Object> params;        public Integer timeoutSeconds;    }    
/**    
 * 指令响应     
*/    
@Data    class CommandResponse {        
public boolean success;        
public String commandId;        
public String errorMessage;    }    
/**    
 * 健康检查     
*/    
@Data    class HealthInfo {        
public boolean online;        public Long latencyMs;        
public String batteryLevel;        
public String firmwareVersion;        
public String message;    }}
