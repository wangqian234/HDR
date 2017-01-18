package com.mvc.service.impl;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.aspectj.weaver.ast.Var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.mvc.dao.WorkRejectDao;
import com.mvc.entity.DepartmentInfo;
import com.mvc.entityReport.WorkHouse;
import com.mvc.entityReport.WorkReject;
import com.mvc.repository.DepartmentInfoRepository;
import com.mvc.service.WorkRejectService;
import com.utils.FileHelper;
import com.utils.StringUtil;
import com.utils.SvgPngConverter;
import com.utils.WordHelper;
import com.base.enums.CleanType;

import net.sf.json.JSONObject;

/**
 * 部门员工做房驳回统计业务层实现
 * 
 * @author zq
 * @date 2017-1-17
 */
@Service("workRejectServiceImpl")
public class WorkRejectServiceImpl implements WorkRejectService {

	@Autowired
	WorkRejectDao workRejectDao;
	@Autowired
	DepartmentInfoRepository departmentInfoRepository;

	@Override
	public List<WorkReject> selectWorkRejectByLimits(Map<String, Object> map) {
		DepartmentInfo departmentInfo = departmentInfoRepository.selectByDeptName("客房部");// 先查询部门id
		map.put("deptId", departmentInfo.getDepartmentId());
		List<Object> listSource = workRejectDao.selectWorkRejectByLimits(map);
		Iterator<Object> it = listSource.iterator();
		List<WorkReject> listGoal = objToWorkReject(it);
		return listGoal;
	}

	// zq员工驳回统计
	private List<WorkReject> objToWorkReject(Iterator<Object> it) {
		List<WorkReject> listGoal = new ArrayList<WorkReject>();
		Object[] obj = null;
		WorkReject workReject = null;
		int i = 0;
		while (it.hasNext()) {
			i++;
			obj = (Object[]) it.next();
			workReject = new WorkReject();
			workReject.setOrderNum(String.valueOf(i));
			workReject.setStaff_name(obj[0].toString());
			workReject.setStaff_no(obj[1].toString());
			workReject.setNum_dust(obj[2].toString());
			workReject.setReject_dust(obj[3].toString());
			workReject.setNum_night(obj[4].toString());
			workReject.setReject_night(obj[5].toString());
			workReject.setNum_leave(obj[6].toString());
			workReject.setReject_leave(obj[7].toString());
			String dust_eff = StringUtil.divide(obj[3].toString(), obj[2].toString());
			workReject.setReject_dust_eff(StringUtil.strFloatToPer(dust_eff));
			String night_eff = StringUtil.divide(obj[5].toString(), obj[4].toString());
			workReject.setReject_night_eff(StringUtil.strFloatToPer(night_eff));
			String leave_eff = StringUtil.divide(obj[7].toString(), obj[6].toString());
			workReject.setReject_leave_eff(StringUtil.strFloatToPer(leave_eff));
			listGoal.add(workReject);
		}
		return listGoal;
	}

	// zq获取员工驳回率分析
	@Override
	public String selectWorkRejectAnalyseByLimits(Map<String, Object> map) {
		JSONObject jsonObject = new JSONObject();
		String year = (String) map.remove("checkYear");
		String quarter = (String) map.remove("quarter");
		String startMonth = null;
		String endMonth = null;
		if (StringUtil.strIsNotEmpty(year) && StringUtil.strIsNotEmpty(quarter)) {
			String startTime = StringUtil.quarterFirstDay(year, quarter);
			String endTime = StringUtil.quarterLastDay(year, quarter);
			map.put("startTime", startTime);
			map.put("endTime", endTime);
			startMonth = startTime.substring(startTime.indexOf("-") + 1, 7);// 截取月份
			endMonth = endTime.substring(endTime.indexOf("-") + 1, 7);
		}
		List<Object> monthList = workRejectDao.selectMonthWorkReject(map);// 获取员工每个月做房驳回效率
		List<String> monthListStr = perMonthEff(monthList, startMonth, endMonth);
		jsonObject.put("list", monthListStr);

		String staffId = (String) map.get("staffId");
		Object[] obj = null;
		String averRejectEff = null;
		Boolean flag = true;
		Long sumWorkTime = (long) 0;
		Long sumRejectTime = (long) 0;
		List<Object> averList = workRejectDao.selectAllAverRejectEff(map);// 获取全体员工平均做房驳回效率
		Iterator<Object> it = averList.iterator();
		while (it.hasNext()) {
			obj = (Object[]) it.next();
			sumWorkTime += Long.valueOf(obj[1].toString());
			sumRejectTime += Long.valueOf(obj[2].toString());
			if (flag) {
				if (obj[0].toString().trim().equals(staffId)) {
					averRejectEff = StringUtil.divide(obj[2].toString(), obj[1].toString());// 员工平均做房驳回效率
					flag = false;
				}
			}
		}
		if (averRejectEff != null) {
			jsonObject.put("averRejectEff", Float.valueOf(averRejectEff));
		}
		String allAverRejectEff = StringUtil.divide(sumRejectTime.toString(), sumWorkTime.toString());
		jsonObject.put("allAverRejectEff", Float.valueOf(allAverRejectEff));// 全体员工平均做房驳回效率
		// 获取驳回原因统计扇形图
		List<Object> reasonList = workRejectDao.selectReasonsByLimits(map);
		Iterator<Object> iter = reasonList.iterator();
		int reasonArr[] = new int[] { 0, 0, 0, 0, 0};

		Object obj1=null;
		while (iter.hasNext()) {
			obj1 = (Object) iter.next();
			JSONObject reasonJson = JSONObject.fromObject(obj1);
			if (reasonJson.containsKey("consumables")) {
				reasonArr[0] += 1;
			}
			if (reasonJson.containsKey("barProblems")) {
				reasonArr[1] += 1;
			}
			if (reasonJson.containsKey("toiletProblems")) {
				reasonArr[2] += 1;
			}
			if (reasonJson.containsKey("towels")) {
				reasonArr[3] += 1;
			}
			if (reasonJson.containsKey("roomProblems")) {
				reasonArr[4] += 1;
			}

		}
		jsonObject.put("reasonList", reasonArr);// 全体员工平均做房驳回效率
		return jsonObject.toString();
	}

	/**
	 * 去掉月份列，缺少的月份的列置为0，同时计算效率
	 * 
	 * @param list
	 * @param startMonth
	 * @param endMonth
	 * @return
	 */
	private List<String> perMonthEff(List<Object> list, String startMonth, String endMonth) {
		List<String> listGoal = new ArrayList<String>();
		if (StringUtil.strIsNotEmpty(startMonth) && StringUtil.strIsNotEmpty(endMonth)) {
			Integer len = Integer.valueOf(endMonth) - Integer.valueOf(startMonth) + 1;
			Integer size = list.size();

			Object[] obj = null;
			Integer month = null;
			for (int i = 0, j = 1; i < size || j <= len; i++, j++) {
				if (i < size) {
					obj = (Object[]) list.get(i);
					month = Integer.valueOf(obj[0].toString());
					if (month == j) {
						listGoal.add(StringUtil.divide(obj[2].toString(), obj[1].toString()));
					} else {
						listGoal.add("0");
						i--;
					}
				} else {
					listGoal.add("0");
				}
			}
		}
		return listGoal;
	}

	// zq导出驳回率统计表
	@Override
	public ResponseEntity<byte[]> exportWorRejectBylimits(Map<String, Object> map, String path, String tempPath) {
		DepartmentInfo departmentInfo = departmentInfoRepository.selectByDeptName("客房部");
		map.put("deptId", departmentInfo.getDepartmentId());
		ResponseEntity<byte[]> byteArr = null;
		try {
			WordHelper<WorkReject> wh = new WordHelper<WorkReject>();
			String fileName = "客房部员工做房驳回率统计表.docx";
			path = FileHelper.transPath(fileName, path);
			OutputStream out = new FileOutputStream(path);
			List<Object> listSource = workRejectDao.selectWorkRejectByLimits(map);
			Iterator<Object> it = listSource.iterator();
			List<WorkReject> listGoal = objToWorkReject(it);

			WorkReject sum = sumWorkReject(listGoal);
			listGoal.add(sum);

			Map<String, Object> listMap = new HashMap<String, Object>();
			listMap.put("0", listGoal);// key存放该list在word中表格的索引，value存放list
			Map<String, Object> contentMap = new HashMap<String, Object>();
			String startTime = (String) map.get("startTime");
			String endTime = (String) map.get("endTime");
			contentMap.put("${startTime}", startTime.substring(0, 10));
			contentMap.put("${endTime}", endTime.substring(0, 10));
			wh.export2007Word(tempPath, listMap, contentMap, 2, out);// 用模板生成word
			out.close();
			byteArr = FileHelper.downloadFile(fileName, path);// 提醒下载

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return byteArr;
	}

	/**
	 * list求和
	 * 
	 * @param list
	 * @return
	 */
	private WorkReject sumWorkReject(List<WorkReject> list) {
		WorkReject sum = new WorkReject();
		Iterator<WorkReject> it = list.iterator();
		Long sum_dust = (long) 0;// 抹尘房
		Long sum_reject_dust = (long) 0;// 驳回次数
		String sum_eff_dust = null;
		Long sum_night = (long) 0;// 过夜
		Long sum_reject_night = (long) 0;// 驳回次数
		String sum_eff_night = null;
		Long sum_leave = (long) 0;// 离退房
		Long sum_reject_leave = (long) 0;// 驳回次数
		String sum_eff_leave = null;
		WorkReject workReject = null;
		while (it.hasNext()) {
			workReject = it.next();
			sum_dust += Integer.valueOf(workReject.getNum_dust());
			sum_reject_dust += Integer.valueOf(workReject.getReject_dust());
			sum_night += Integer.valueOf(workReject.getNum_night());
			sum_reject_night += Integer.valueOf(workReject.getReject_night());
			sum_leave += Integer.valueOf(workReject.getNum_leave());
			sum_reject_leave += Integer.valueOf(workReject.getReject_leave());
		}
		sum.setOrderNum("总计");
		sum.setNum_dust(String.valueOf(sum_dust));// 抹尘
		sum.setReject_dust(String.valueOf(sum_reject_dust));
		sum_eff_dust = StringUtil
				.strFloatToPer(StringUtil.divide(String.valueOf(sum_reject_dust), String.valueOf(sum_dust)));
		sum.setReject_dust_eff(sum_eff_dust);
		sum.setNum_night(String.valueOf(sum_night));// 过夜
		sum.setReject_night(String.valueOf(sum_reject_night));
		sum_eff_night = StringUtil
				.strFloatToPer(StringUtil.divide(String.valueOf(sum_reject_night), String.valueOf(sum_night)));
		sum.setReject_night_eff(sum_eff_night);
		sum.setNum_leave(String.valueOf(sum_leave));// 离退
		sum.setReject_leave(String.valueOf(sum_reject_leave));
		sum_eff_leave = StringUtil
				.strFloatToPer(StringUtil.divide(String.valueOf(sum_reject_leave), String.valueOf(sum_leave)));
		sum.setReject_leave_eff(sum_eff_leave);
		return sum;
	}

	// zzq做房驳回率分析
	@Override
	public ResponseEntity<byte[]> exportWorkRejectAna(Map<String, Object> map, String path, String tempPath,
			String picPath) {
		DepartmentInfo departmentInfo = departmentInfoRepository.selectByDeptName("客房部");
		map.put("deptId", departmentInfo.getDepartmentId());
		String staffName = (String) map.get("staffName");
		String year = (String) map.get("checkYear");
		String quarter = (String) map.get("quarter");
		String cleanType = (String) map.get("cleanType");
		String cleanTypeStr = CleanType.intToStr(Integer.valueOf(cleanType));
		ResponseEntity<byte[]> byteArr = null;
		try {
			WordHelper<WorkHouse> wh = new WordHelper<WorkHouse>();
			String fileName = "客房部员工" + staffName + cleanTypeStr + "做房驳回率分析.docx";
			path = FileHelper.transPath(fileName, path);// 解析后的上传路径
			OutputStream out = new FileOutputStream(path);
			Map<String, Object> contentMap = new HashMap<String, Object>();
			contentMap.put("${staffName}", staffName);
			contentMap.put("${cleanType}", cleanTypeStr);
			if (StringUtil.strIsNotEmpty(year) && StringUtil.strIsNotEmpty(quarter)) {
				String startTime = StringUtil.quarterFirstDay(year, quarter);
				String endTime = StringUtil.quarterLastDay(year, quarter);
				startTime = startTime.substring(0, 10);// 保留到天
				endTime = endTime.substring(0, 10);
				contentMap.put("${startTime}", startTime);
				contentMap.put("${endTime}", endTime);
			}

			// 图片相关
			String[] svgs = new String[2];
			svgs[0] = (String) map.get("chartSVGStr");
			svgs[1] = (String) map.get("chart1SVGStr");
			String[] picNames = new String[2];
			String[] picPaths = new String[2];
			Map<String, Object> picMap = null;
			for (int i = 0; i < 2; i++) {
				if (StringUtil.strIsNotEmpty(svgs[i])) {
					picNames[i] = "pic" + i + ".png";
					picPaths[i] = FileHelper.transPath(picNames[i], picPath);// 解析后的上传路径
					picMap = new HashMap<String, Object>();
					picMap.put("width", 960);
					picMap.put("height", 480);
					picMap.put("type", "png");
					try {
						SvgPngConverter.convertToPng(svgs[i], picPaths[i]);// 图片svgCode转化为png格式，并保存到picPath[i]
						picMap.put("content", FileHelper.inputStream2ByteArray(new FileInputStream(picPaths[i]), true));
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					contentMap.put("${pic" + i + "}", picMap);
				}
			}
			wh.export2007Word(tempPath, null, contentMap, 2, out);// 用模板生成word
			out.close();
			byteArr = FileHelper.downloadFile(fileName, path);// 提醒下载
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return byteArr;
	}
}
