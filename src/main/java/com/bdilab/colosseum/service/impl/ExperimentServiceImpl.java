package com.bdilab.colosseum.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bdilab.colosseum.benchmark.execute.MultiAgExecutor;
import com.bdilab.colosseum.benchmark.factory.SutConfigFactory;
import com.bdilab.colosseum.benchmark.factory.WorkloadFactory;
import com.bdilab.colosseum.benchmark.sutconfig.base.SutConfig;
import com.bdilab.colosseum.benchmark.workload.base.Workload;
import com.bdilab.colosseum.bo.ExperimentBO_V2;
import com.bdilab.colosseum.bo.SoftwareLocationBO;
import com.bdilab.colosseum.domain.Algorithm;
import com.bdilab.colosseum.domain.Experiment;
import com.bdilab.colosseum.domain.ExperimentRun;
import com.bdilab.colosseum.domain.SystemWorkload;
import com.bdilab.colosseum.enums.ComponentType;
import com.bdilab.colosseum.enums.ExperimentRunningStatus;
import com.bdilab.colosseum.exception.BusinessException;
import com.bdilab.colosseum.global.Constant;
import com.bdilab.colosseum.global.Context;
import com.bdilab.colosseum.mapper.*;
import com.bdilab.colosseum.response.HttpCode;
import com.bdilab.colosseum.response.ResponseResult;
import com.bdilab.colosseum.service.ExperimentService;
import com.bdilab.colosseum.service.PipelineService;
import com.bdilab.colosseum.utils.FileUtils;
import com.bdilab.colosseum.utils.MapUtils;
import com.bdilab.colosseum.utils.NumberUtils;
import com.bdilab.colosseum.utils.XlsUtils;
import com.bdilab.colosseum.vo.*;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.util.*;

/**
 * @ClassName ExperimentServiceImpl
 * @Author wx
 * @Date 2021/1/4 0004 9:52
 **/
@Service
public class ExperimentServiceImpl implements ExperimentService {

    @Resource
    ExperimentMapper experimentMapper;
    @Resource
    SystemEnvMapper systemEnvMapper;
    @Resource
    ExperimentRunMapper experimentRunMapper;
    @Resource
    AlgorithmMapper algorithmMapper;
    @Resource
    PerformanceGetMethodMapper performanceGetMethodMapper;
    @Resource
    UserSutMapper userSutMapper;
    @Resource
    SystemWorkloadMapper systemWorkloadMapper;
    @Autowired
    PipelineService pipelineService;
    @Resource
    ComponentMapper componentMapper;

    @Value("${result.path}")
    private String resultPath;

    @Value("${image.map}")
    private String imageMap;

    @Value("${logo.path}")
    private String logoPath;

    /**
     * ????????????
      * @param experimentName
     * @param description
     * @param userId
     * @param sysEnvId
     * @param allParams
     * @param blackList
     * @param whiteList
     * @param algorithmIds
     * @param metricsSetting
     * @param resultSetting
     * @return
     */
    @Override
    public int add(String experimentName, String description, Long userId, Long sysEnvId, String allParams,
                   String blackList, String whiteList, String algorithmIds, String performance,
                   String metricsSetting, String resultSetting, MultipartFile logo) {
        boolean uploadSuccess=true;
        //??????Logo???null??????????????????????????????????????????Logo???
        String path="";
        if(logo!=null){
            String logoPath1=this.logoPath+userId;
            String[] strs=logo.getOriginalFilename().split("\\.");
            String fileName="experiment_"+UUID.randomUUID() +"."+strs[strs.length-1];
            uploadSuccess= FileUtils.uploadFileRename(logo,logoPath1,fileName);
            path=logoPath1+File.separator+fileName;
        }
        if(uploadSuccess){
            Experiment experiment = Experiment.builder()
                    .experimentName(experimentName)
                    .description(description)
                    .fkUserId(userId)
                    .fkSysEnvId(sysEnvId)
                    .allParams(allParams)
                    .blackList(blackList)
                    .whiteList(whiteList)
                    .fkAlgorithmIds(algorithmIds)
                    .performance(performance)
                    .metricsSetting(metricsSetting)
                    .resultSetting(resultSetting)
                    .logoPath(path)
                    .build();
            return experimentMapper.insertSelective(experiment);
        }
        return 0;
    }

    /**
     * ?????????????????????????????????
     * @param algorithmIds
     * @return
     */
    @Override
    public boolean checkAlgorithms(List<Long> algorithmIds,Long userId) {
        int count = algorithmMapper.countValidAlgorithmNums(algorithmIds, userId);
        return algorithmIds.size() == count;
    }

    /**
     * ????????????
     * @param experimentId
     * @param description
     * @param sysEnvId
     * @param allParams
     * @param blackList
     * @param whiteList
     * @param algorithmIds
     * @param metricsSetting
     * @param resultSetting
     * @return
     */
    @Override
    public int update(Long experimentId, String description, Long sysEnvId, String allParams, String blackList, String whiteList,
                      String algorithmIds, String performance, String metricsSetting, String resultSetting,MultipartFile logo) {
        boolean uploadSuccess=true;
        String path=null;
        if(logo!=null){
            //???????????????logo??????
            Experiment experiment = experimentMapper.selectByPrimaryKey(experimentId);
            if (!experiment.getLogoPath().equals("")){
                File file=new File(experiment.getLogoPath());
                file.delete();
            }
            Long userId=experiment.getFkUserId();
            String logoPath1=this.logoPath+userId;
            String[] strs=logo.getOriginalFilename().split("\\.");
            String fileName="experiment_"+UUID.randomUUID() +"."+strs[strs.length-1];
            uploadSuccess=FileUtils.uploadFileRename(logo,logoPath1,fileName);
            path=logoPath1+File.separator+fileName;
        }
        Experiment experiment = Experiment.builder()
                .id(experimentId)
                .description(description)
                .fkSysEnvId(sysEnvId)
                .allParams(allParams)
                .blackList(blackList)
                .whiteList(whiteList)
                .fkAlgorithmIds(algorithmIds)
                .performance(performance)
                .metricsSetting(metricsSetting)
                .resultSetting(resultSetting)
                .logoPath(path)
                .build();
        return experimentMapper.updateByPrimaryKeySelective(experiment);
    }

    /**
     * ?????????????????????????????????
     *
     * @param userId
     * @return
     */
    @Override
    public List<ExperimentVO> getList(Long userId) {
        List<ExperimentVO> experimentVOS = experimentMapper.selectByUserId(userId);
        for(ExperimentVO experimentVO:experimentVOS){
            if(!(experimentVO.getLogoPath().equals(""))){
                experimentVO.setLogoPath(imageMap+experimentVO.getLogoPath());
            }
        }
        return experimentVOS;
    }

    /**
     * ??????experimentId????????????????????????
     *
     * @param experimentId
     * @return
     */
    @Override
    public boolean checkByExperimentId(Long experimentId) {
        return experimentMapper.checkById(experimentId) > 0;
    }

    @Override
    public boolean checkRunningExperimentByExperimentId(Long experimentId) {
        return experimentRunMapper.checkRunningExperimentByExperimentId(experimentId) > 0;
    }

    /**
     * ??????experimentId????????????????????????
     *
     * @param experimentId
     * @return
     */
    @Override
    public ExperimentDetailVO getByExperimentId(Long experimentId) {
        ExperimentDetailVO experimentDetailVO = experimentMapper.selectByExperimentId(experimentId);
        ArrayList<Long> algorithmIds = new ArrayList<>();
        for (Object o : JSONArray.parseArray(experimentDetailVO.getFkAlgorithmIds())) {
            algorithmIds.add(Long.parseLong(o.toString()));
        }
        experimentDetailVO.setAlgorithmVOS(algorithmMapper.selectAgForEpDetailVOByAgIds(algorithmIds));
        experimentDetailVO.setSystemEnv(systemEnvMapper.selectBySysEnvId(experimentDetailVO.getSysEnvId()));
        return experimentDetailVO;
    }

    /**
     * ??????????????????????????????????????????????????????void???
     *
     * @param experimentId
     */
    @Override
    @Transactional
    public Map<String, Object> run(Long experimentId, Map<String,String> execParams, Integer restrictNumber) {
        //????????????id??????????????????pipeline_id?????????????????????
        //????????????????????????????????????
        ExperimentBO_V2 experimentBO = experimentMapper.selectExperimentBOByExperimentId_V2(experimentId);
        // ????????????????????????????????????????????????
        ArrayList<ArrayList<String>> parameter = new ArrayList<>();
        // ?????????????????????????????????
        //addComponentParam(parameter,experimentBO.getParamValue());

        // ???experiment_id?????????experiment_run???
        ExperimentRun experimentRun = new ExperimentRun();
        experimentRun.setFkExperimentId(experimentId);
        experimentRunMapper.insertSelective(experimentRun);
        
        // ???conversionId?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????sse?????????????????????????????????
        String conversationId = UUID.randomUUID().toString();

        //??????????????????
        addConfigParam(parameter,conversationId,restrictNumber);

        // ??????envId??????sutName ??? sutVersion
        Map<String,Object> sutNameAndSutVersion = systemEnvMapper.selectSutNameAndSutVersionByEnvId(experimentBO.getFkSysEnvId());
        String sutName = sutNameAndSutVersion.get("sut_name").toString();
        String sutVersion = sutNameAndSutVersion.get("sut_version").toString();

        String performanceFilePath = System.getProperty("user.dir") + "/file/sys/performanceFile/"+sutName+"-performance.xls";
        String performance = null;
        // ??????????????????xxx_min ??? xxx_max
        try {// TODO performance????????????????????????????????????
            performance = experimentBO.getPerformance() + "_"
                    + XlsUtils.readExcel(performanceFilePath,experimentBO.getPerformance(),Constant.MIN_MAX_ROW_NUMBER);
        } catch (Exception e) {
            throw new BusinessException(HttpCode.BAD_REQUEST,"????????????????????????????????????",e);
        }

        //??????????????????
        addSutParam(parameter, generateParams(experimentBO.getAllParams()),experimentBO.getBlackList(),experimentBO.getWhiteList(),performance);

        // ??????envId??????workloadName ??? WorkloadVersion
        Map<String, Object> workloadNameAndWorkloadVersion = systemEnvMapper.selectWorkloadNameAndWorkloadVersionByEnvId(experimentBO.getFkSysEnvId());
        String workloadName = workloadNameAndWorkloadVersion.get("workload_name").toString();
        String workloadVersion = workloadNameAndWorkloadVersion.get("workload_version").toString();

        //#############????????????????????? sutConfig + workload - START #############
        SoftwareLocationBO softwareLocationBO = systemEnvMapper.selectSoftWareLocationByEnvId(experimentBO.getFkSysEnvId());
        Map<String,Object> commonParams = null;
        Map<String,Object> contextParams = new HashMap<>();
        try {
            commonParams = MapUtils.objectToMap(softwareLocationBO);
        } catch (IllegalAccessException e) {
            throw new BusinessException(HttpCode.BAD_REQUEST,"softwareLocationBO -> commonParams??????",e);
        }
        contextParams.put("experimentRunId",experimentRun.getId());
        contextParams.put("envId",experimentBO.getFkSysEnvId());
        //???????????????sutname + version ?????????????????????sutConfig
        SutConfig sutConfig = SutConfigFactory.getSutConfig(sutName, sutVersion);
        sutConfig.setPerformanceFilePath(performanceFilePath);
        //???????????????workloadName + version ?????????????????????workload
        Workload workload = WorkloadFactory.getWorkload(workloadName, workloadVersion);
        workload.setSutConfig(sutConfig);
//        workload.init(execParams,commonParams,contextParams);
//        Context.getInstance().putWorkload(conversationId, workload);

        // ?????????????????????????????????runId
//        String runId = pipelineService.createRun(experimentBO.getPipelineId(),"pipelineName" + UUID.randomUUID(), parameter);
//        System.out.println("pipeline???????????????????????????runId???" + runId);

        //???????????????????????????????????????????????????????????????contextParams???execParams???????????????
        JSONObject jsonObject=new JSONObject();
        jsonObject.put("experimentRunId",contextParams.get("experimentRunId"));
        jsonObject.put("envId",contextParams.get("envId"));
        JSONObject jsonObject1=new JSONObject();
        for(String key:execParams.keySet()){
            jsonObject1.put(key,execParams.get(key));
        }
        jsonObject.put("execParam",jsonObject1);
        /*jsonObject.put("result","[]");*/

        String resultFilePath =  resultPath + "result_" +experimentRun.getId() +".json";
        File resultFile = new File(resultFilePath);
        try {
            if (!resultFile.exists()) {
                resultFile.createNewFile();
            }
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(resultFile)));
            bw.write(jsonObject.toString());
            bw.close();
        } catch (IOException e) {
            throw new BusinessException(HttpCode.BAD_REQUEST,"????????????????????????",e);
        }

        contextParams.put("resultFilePath",resultFilePath);

        workload.init(execParams,commonParams,contextParams);
        Context.getInstance().putWorkload(conversationId, workload);
        //#############????????????????????? sutConfig + workload - END #############

        //#####################???????????????????????????????????????????????? - START ########################
        JSONArray fkAlgorithmIds = JSONArray.parseArray(experimentBO.getFkAlgorithmIds());
        List<MultiAgExecutor.ExecutorAg> executorAgs = new ArrayList<>();
        for (Object fkAlgorithmId : fkAlgorithmIds) {
            Algorithm algorithm = algorithmMapper.selectByPrimaryKey(((Integer)fkAlgorithmId).longValue());
            MultiAgExecutor.ExecutorAg executorAg = new MultiAgExecutor.ExecutorAg();
            executorAg.setId(algorithm.getId());
            executorAg.setAlgorithmName(algorithm.getAlgorithmName());
            executorAg.setParamValue(algorithm.getParamValue());
            executorAg.setPipelineId(algorithm.getPipelineId());

            String componentNamesJoinInString = pipelineService.getComponentNamesJoinInString(algorithm.getGgeditorObjectString());
            String buildInAlgorithmName = Constant.BUILD_IN_ALGORITHM.getOrDefault(componentNamesJoinInString,"NONE");

            executorAg.setBuildInAlgorithmName(buildInAlgorithmName);
            executorAgs.add(executorAg);
        }
        MultiAgExecutor executor = new MultiAgExecutor(parameter,executorAgs);
        Context.getInstance().putExecutor(conversationId,executor);
        //#####################???????????????????????????????????????????????? - END ########################

        String runId = executor.execute(pipelineService);
        if(runId==null){
            throw new BusinessException(HttpCode.BAD_REQUEST,"???????????????????????????Kubeflow????????????");
        }
        experimentRun.setRunId("[\""+runId+"\"]");
        // ??????????????????????????????????????????????????????????????????????????????java??????????????????????????????????????????
        experimentRun.setStatus(ExperimentRunningStatus.RUNNING.getValue());
        experimentRun.setResult(resultFilePath);
        experimentRun.setStartTime(new Date());
        int update = experimentRunMapper.updateByPrimaryKeySelective(experimentRun);
        Map<String, Object> resultMap = new HashMap<>();
        if (update>0){
            resultMap.put("conversationId", conversationId);
            resultMap.put("isSucceed", true);
        }else {
            resultMap.put("conversationId", conversationId);
            resultMap.put("isSucceed", false);
        }
        return resultMap;
    }


    private void addConfigParam(ArrayList<ArrayList<String>> parameter, String conversationId, Integer restrictNumber){
        HashMap<String,Object> map = new HashMap<>();
        map.put("conversationId",conversationId);
        map.put("ip_port",Constant.IP_PORT);
        map.put("output_dir",Constant.OUTPUT_DIR);
        map.put("performance_retry_times",Constant.PERFORMANCE_RETRY_TIMES);
        map.put("report_retry_times",Constant.REPORT_RETRY_TIMES);
        map.put("report_retry_interval",Constant.REPORT_RETRY_INTERVAL);
        map.put("restrict_number",restrictNumber);
        String config_params = JSONObject.toJSONString(map);
        ArrayList<String> list = new ArrayList<>();
        list.add(Constant.CONFIG_PARAMS);
        list.add(config_params);
        parameter.add(list);
    }

    private void addSutParam(ArrayList<ArrayList<String>> parameter,String all_params,String black_list,String white_list, String performance){
        HashMap<String,String> map = new HashMap<>();
        map.put("all_params",all_params);
        map.put("black_list",(black_list==null||black_list.equals(""))?"[]":black_list.replaceAll("\"","'"));
        map.put("white_list",(white_list==null||white_list.equals(""))?"[]":white_list.replaceAll("\"","'"));
        map.put("performance",performance);

        String common_params = JSONObject.toJSONString(map);
        ArrayList<String> list = new ArrayList<>();
        list.add(Constant.COMMON_PARAMS);
        list.add(common_params);
        parameter.add(list);
    }

    /**
     * python??????????????????experimentRunId??????????????????experiment_run??????????????????conversationId+sse????????????????????????????????????resultFilePath??????????????????
     *
     * @param experimentRunId
     * @param conversationId
     * @param resultFilePath
     * @return
     */
    @Override
    public boolean pushData(Long experimentRunId, String conversationId, String componentName, String resultFilePath) {
        // ??????componentName?????????????????????type?????????????????????????????????
        Integer type = componentMapper.selectTypeByComponentName(componentName);
        if (!ComponentType.OPTIMAL_PARAMETER_SEARCH.getType().equals(type)) {
            System.out.println(componentName+"????????????");
//            ProcessSseEmitters.sendEvent(conversationId, "?????? " + componentName + " ???????????????");
        } else {
            System.out.println(componentName+"????????????");
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("status", "finished");
            // ??????????????????????????????????????????????????????
            System.out.println("???????????????????????????" + resultFilePath);

            // ??????experimentRunId???experiment_run?????????id??????experiment_run?????????
            ExperimentRun experimentRun = experimentRunMapper.selectByPrimaryKey(experimentRunId);
            experimentRun.setStatus(ExperimentRunningStatus.RUNNINGSUCCESS.getValue());
            // ?????????????????????????????????
            experimentRun.setResult(resultFilePath);
            experimentRun.setEndTime(new Date());
            experimentRunMapper.updateByPrimaryKey(experimentRun);

            ResponseResult responseResult = new ResponseResult(HttpCode.OK.getCode(), componentName + "?????????????????????????????????", true, resultMap);
            System.out.println(responseResult.toString());
//            ProcessSseEmitters.sendEvent(conversationId, responseResult);
//            // ????????????
//            ProcessSseEmitters.getSseEmitterByKey(conversationId).complete();
//            // ??????sse??????
//            ProcessSseEmitters.removeSseEmitterByKey(conversationId);
        }
        return true;
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     * @param params
     * @return
     */
    private String generateParams(String params) {
        StringBuilder result = new StringBuilder();
        // ???params??????json??????
        JSONArray jsonArray = JSONArray.parseArray(params);
        // ????????????{'a':['int', [0, 50], 0],'b':['enum',['on','off'],'on'],'c':['float',[0.1,0.2],0.1]}
        result.append("{");
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = JSONObject.parseObject(jsonArray.getString(i));
            // ???????????????,??????
            if (jsonObject.get("validValue")!=null) {
                result.append("'").append(jsonObject.get("name")).append("':['")
                        .append(transType(jsonObject.get("type").toString())).append("',")
                        .append("['").append(jsonObject.get("validValue").toString().replaceAll(";", "','")).append("']").append(",")
                        .append("'").append(jsonObject.get("defaultValue")).append("'").append("],");
            } else {
                result.append("\'").append(jsonObject.get("name")).append("\':[\'")
                        .append(transType(jsonObject.get("type").toString())).append("\',[")
                        .append(jsonObject.get("minValue")).append(",")
                        .append(jsonObject.get("maxValue")).append("],")
                        .append(jsonObject.get("defaultValue")).append("],");
            }
        }
        result.replace(result.length()-1, result.length(), "}");
        return result.toString();
    }

    /**
     * ??????java??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * @param oldType
     * @return
     */
    private String transType(String oldType) {
        if ("Integer".equals(oldType)) {
           return "int";
        } else if ("Float".equals(oldType)) {
           return "float";
        } else if ("Boolean".equals(oldType) || "Enumeration".equals(oldType)) {
           return "enum";
        }
        return "";
    }

//    public static void main(String[] args) {
//        ExperimentServiceImpl e = new ExperimentServiceImpl();
//        System.out.println(e.generateParams("[{\"name\":\"admin_port\",\"type\":\"Integer\",\"minValue\":\"0\",\"maxValue\":\"65535\",\"validValue\":null,\"defaultValue\":\"5\"},{\"name\":\"audit_log_buffer_size\",\"type\":\"Integer\",\"minValue\":\"4096\",\"maxValue\":\"18446744073709547520\",\"validValue\":null,\"defaultValue\":\"4099\"}," +
//                "{\"name\":\"innodb_adaptive_hash_index\",\"type\":\"Boolean\",\"minValue\":null,\"maxValue\":null,\"validValue\":[\"on\",\"off\"],\"defaultValue\":\"on\"}]"));
//    }

    /**
     * ??????????????????????????????
     * @return
     */
    @Override
    public List<JSONObject> getResultList(Long experimentId) {
//        List<ExperimentRun> experimentRunList = experimentRunMapper.selectByExperimentId(experimentId);
//        for (ExperimentRun experimentRun : experimentRunList) {
//            if (ExperimentRunningStatus.RUNNING.getValue() == experimentRun.getStatus()) {
//                // ???????????????????????????????????????
//                if (pipelineService.checkFailedStatus(experimentRun.getRunId())); {
//                    experimentRun.setStatus(ExperimentRunningStatus.RUNNINGFAIL.getValue());
//                    experimentRun.setEndTime(new Date());
//                    experimentRunMapper.updateByPrimaryKeySelective(experimentRun);
//                }
//            }
//        }
//        return experimentRunMapper.selectByExperimentIdAndUserId(experimentId);
        System.out.println(experimentId);
        List<ExperimentRun> experimentRuns = experimentRunMapper.selectByExperimentId(experimentId);
        System.out.println(experimentRuns.size());
        List<JSONObject> list=new ArrayList<>();
        SystemWorkload systemWorkload=null;
        for(ExperimentRun experimentRun:experimentRuns){
            JSONObject jsonObject1=new JSONObject();
            jsonObject1.put("id",experimentRun.getId());
            jsonObject1.put("runId",experimentRun.getRunId());

            //???????????????????????????????????????
            String resultFilePath =  experimentRun.getResult();
            File resultFile = new File(resultFilePath);
            String s=null;
            JSONObject js=null;
            String target=null;
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(resultFile)));
                InputStream is = new FileInputStream(resultFile);
                js = JSONObject.parseObject(IOUtils.toString(is, "utf-8"));
                //1. ??????????????????
                Experiment experiment=experimentMapper.selectByPrimaryKey(experimentRun.getFkExperimentId());
                String performance=experiment.getPerformance();
                SystemEnvVO systemEnvVO = systemEnvMapper.selectBySysEnvId(experiment.getFkSysEnvId());
                //2. ?????????????????????max ????????? min???max???????????????????????????min??????????????????????????????,???????????????target???
                systemWorkload=systemWorkloadMapper.selectByNameAndVersion(systemEnvVO.getWorkloadName(),systemEnvVO.getWorkloadVersion());
                String performanceFilePath=System.getProperty("user.dir")+systemWorkload.getPerformance();
                if (performanceFilePath!=null){
                    List<PerformanceVo> performanceFromXls = XlsUtils.getPerformanceFromXls(performanceFilePath);
                    for(PerformanceVo pv:performanceFromXls){
                        if(pv.getActualPerformance().equalsIgnoreCase(performance)){
                            target=pv.getMinOrMax();
                            break;
                        }
                    }
                }
                List<Float> list1=new ArrayList<>();
                for(String k:js.keySet()){
                    if(!k.equals("experimentRunId")&&!k.equals("envId")&&!k.equals("execParam")){
                        list1.add(Float.parseFloat(js.getJSONObject(k).getJSONObject("data").get(performance).toString()));
                    }
                }
                if(target.equals("max")){
                    s=performance+"="+Collections.max(list1);
                }
                if(target.equals("min")){
                    s=performance+"="+Collections.min(list1);
                }

                is.close();
                br.close();
            }catch (Exception e){
                e.printStackTrace();
            }
            jsonObject1.put("result",s);
            jsonObject1.put("status",experimentRun.getStatus());
            jsonObject1.put("start time",experimentRun.getStartTime());
            jsonObject1.put("end time",experimentRun.getEndTime());
            list.add(jsonObject1);
        }
        return list;
    }

    /**
     * ??????resultId??????????????????????????????
     *
     * @param resultId
     * @return
     */
    @Override
    public boolean checkByResultId(Long resultId) {
        return experimentRunMapper.checkById(resultId) > 0;
    }

    /**
     * ??????resultId????????????????????????
     *
     * @param resultId
     *
     * @return
     */
    @Override
    public ExperimentResultDetailVO getResultDetail(Long resultId) {
        ExperimentRun experimentRun = experimentRunMapper.selectByPrimaryKey(resultId);
        // ?????????????????????status????????????????????????????????????kubeflow????????????????????????????????????????????????????????????????????????
        if (ExperimentRunningStatus.RUNNING.getValue().equals(experimentRun.getStatus())) {
            // ???????????????????????????????????????
           if (pipelineService.checkFailedStatus(experimentRun.getRunId())) {
                experimentRun.setStatus(ExperimentRunningStatus.RUNNINGFAIL.getValue());
                experimentRun.setEndTime(new Date());
                experimentRunMapper.updateByPrimaryKeySelective(experimentRun);
            }
        }
        ExperimentResultDetailVO experimentResultDetailVO = experimentRunMapper.selectByResultId(resultId);
        // ???csv???????????????
        if (experimentResultDetailVO.getResult() != null) {
            String filePath = experimentResultDetailVO.getResult();
            // ??????csv??????
            List<List<String>> result = XlsUtils.getCellFromCSV(filePath);
            experimentResultDetailVO.setResult(result.toString());
        }
        experimentResultDetailVO.setExperiment(experimentMapper.selectByExperimentId(experimentResultDetailVO.getExperimentId()));
        return experimentResultDetailVO;
    }

//    @Override
//    public boolean terminate(Long resultId) {
//        // ??????resultId??????runId
//        ExperimentRun experimentRun = experimentRunMapper.selectByPrimaryKey(resultId);
//        // ?????????????????????status
//        experimentRun.setStatus(ExperimentRunningStatus.RUNNINGFAIL.getValue());
//        experimentRun.setEndTime(new Date());
//        return experimentRunMapper.updateByPrimaryKeySelective(experimentRun) > 0;
//    }

    /**
     * ????????????????????????paramList,performance,????????????????????????workload??????????????????
     * @param workload
     * @param performance
     * @param paramList
     * @return
     */
    @Override
    public String getPerformance(Workload workload, String paramList, String performance) {
        Map<String,Object> params = new HashMap<>();
        params.put("paramList",paramList);
        params.put("performance",performance);
        workload.start(params);
        String result = workload.getResult();
        System.out.println("result="+result);
        workload.clean();
        return NumberUtils.isNumber(result)?result: null;
    }

    /**
     * ????????????ID????????????
     *
     * @param experimentId
     * @return
     */
    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean delete(Long experimentId) {
        // ????????????ID????????????????????????
        List<ExperimentRun> experimentRuns = experimentRunMapper.selectByExperimentId(experimentId);
        // ??????runId??????kubeflow????????????
        for (ExperimentRun experimentRun : experimentRuns) {
            String[] strs=experimentRun.getRunId().replaceAll("(\"|\\[|])","")
                    .split(",");
            for(String str:strs){
                pipelineService.deleteRunById(str);
            }
        }
        // ??????????????????????????????????????????????????????
        int delete = experimentRunMapper.deleteByExperimentId(experimentId);
        if (delete < 0) {
            return false;
        }
        //??????????????????
        for(ExperimentRun experimentRun : experimentRuns) {
            String resultFilePath =  resultPath + "result_"+experimentRun.getId() +".json";
            File file = new File(resultFilePath);
            if (!file.exists()) {
                System.out.println("Failed to delete file:" + resultFilePath + "does not exist???");
                return false;
            } else {
                file.delete();
            }
        }
        //???????????????
        return experimentMapper.deleteByPrimaryKey(experimentId) > 0;
    }

    /**
     * ??????runID???????????????????????????
     * @param conversationId
     * @param data
     * @return
     */
    @Override
    public int saveResult(String conversationId,String data){
        ExperimentRun experimentRun=new ExperimentRun();
        experimentRun.setRunId(conversationId);
        experimentRun.setResult(data);
        experimentRun.setStatus(ExperimentRunningStatus.RUNNINGSUCCESS.getValue());
        experimentRun.setEndTime(new Date());
        return experimentRunMapper.updateResultByRunId(experimentRun);
    }
    /**
     * ????????????ID???????????????????????????runId
     *
     * @param experimentId
     * @return
     */
    @Override
    public String getNewestRunIdbyExperimentId(Long experimentId){
        List<String> list=experimentRunMapper.selectRunIdByExperimentId(experimentId);
        return list.get(0);
    }

    @Override
    public void updateRunIdINExperimentRun(Long experimentRunId, String runId) {
        String runIds = experimentRunMapper.selectByPrimaryKey(experimentRunId).getRunId();
        JSONArray jsonArray = JSONObject.parseArray(runIds);
        jsonArray.add(runId);
        ExperimentRun experimentRun = new ExperimentRun();
        experimentRun.setId(experimentRunId);
        experimentRun.setRunId(jsonArray.toJSONString());
        experimentRunMapper.updateByPrimaryKeySelective(experimentRun);
    }

    @Override
    public void updateStatusINExperimentRun(Long experimentRunId, ExperimentRunningStatus status, boolean isEnd) {
        ExperimentRun experimentRun = new ExperimentRun();
        experimentRun.setId(experimentRunId);
        experimentRun.setStatus(status.getValue());
        if(isEnd){
            experimentRun.setEndTime(new Date());
        }
        experimentRunMapper.updateByPrimaryKeySelective(experimentRun);
    }

    @Override
    public ExperimentRun getExperimentByRunId(Long experimentRunId){
        return experimentRunMapper.selectByPrimaryKey(experimentRunId);
    }

    @Override
    public List<Experiment> getExperimentByuserId(Long userId){
        return experimentMapper.selectAllExperimentsByUserId(userId);
    }
}
