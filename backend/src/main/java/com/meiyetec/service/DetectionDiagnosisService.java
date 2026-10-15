package com.meiyetec.service;
import com.meiyetec.entity.SkinDetection;
import com.meiyetec.entity.HairDetection;
import com.meiyetec.entity.NailDetection;
import java.util.List;
/**
 * 诊断引擎
 * 根据检测数据自动诊断肤质/发质/甲面问题 
*/
public interface DetectionDiagnosisService {    
/**    
 * 诊断肤质     
*/    String diagnoseSkin(SkinDetection detection);    
/**    
 * 诊断发质     
*/    String diagnoseHair(HairDetection detection);    
/**    
 * 诊断甲面     
*/    String diagnoseNail(NailDetection detection);    
/**    
 * 综合诊断所有     
*/    List<String> diagnoseAll(Long memberId);    
/**    
 * 智能识别问题主诉     
*/    List<String> identifyConcerns(SkinDetection skin);    List<String> identifyConcerns(HairDetection hair);    List<String> identifyConcerns(NailDetection nail);}
