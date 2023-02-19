package net.corda.explorer.service.impl;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.CryptoUtils;
import net.corda.core.crypto.SecureHash;
import net.corda.core.internal.TransactionDeserialisationException;
import net.corda.core.transactions.CoreTransaction;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.WireTransaction;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.explorer.exception.GenericException;
import net.corda.explorer.exception.UnsupportedFlowParamException;
import net.corda.explorer.model.common.FlowInfo;
import net.corda.explorer.model.common.FlowParam;
import net.corda.explorer.model.response.FlowData;
import net.corda.explorer.model.response.TransactionList;
import net.corda.explorer.rpc.NodeRPCClient;
import net.corda.explorer.service.ExplorerService;
import net.corda.explorer.service.SettingsService;
import net.corda.explorer.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    @Autowired
    private ExplorerService explorerService;

    @Autowired
    private SettingsService settingsService;

    private List<File> jarFiles;

    private List<String> typeList =  new ArrayList<>(
            Arrays.asList("net.corda.core.identity.Party",
                    "java.lang.String",
                    "java.lang.StringBuilder",
                    "java.lang.StringBuffer",
                    "java.lang.Long",
                    "long",
                    "java.lang.Integer",
                    "int",
                    "java.land.Double",
                    "double",
                    "java.lang.Float",
                    "float",
                    "java.math.BigDecimal",
                    "java.math.BigInteger",
                    "java.lang.Boolean",
                    "boolean",
                    "java.util.UUID",
                    "net.corda.core.contracts.UniqueIdentifier",
                    "net.corda.core.contracts.Amount",
                    "java.time.LocalDateTime",
                    "java.time.LocalDate",
                    "java.time.Instant",
                    "net.corda.core.crypto.SecureHash",
                    "java.util.List",
                    "java.util.Set",
                    "net.corda.core.utilities.OpaqueBytes"
            )
    );

    @Override
    public FlowData getFlowList() {
        FlowData flowData = new FlowData();
        List<File> jarFiles = loadCorDappsToClassPath();
        this.jarFiles = jarFiles;
        flowData.setFlowInfoList(loadFlowsInfoFromJarFiles(jarFiles, NodeRPCClient.getRpcProxy().registeredFlows()));
        return flowData;
    }

    @Override
    @SuppressWarnings( "deprecation" )
    public TransactionList getTransactionList(int pageSize, int offset) {
        List<SignedTransaction> signedTransactions = NodeRPCClient.getRpcProxy().internalVerifiedTransactionsSnapshot();
        TransactionList transactionList = new TransactionList();
        List<TransactionList.TransactionData> transactionDataList = new ArrayList<>();
        transactionList.setTotalRecords(signedTransactions.size());
        int initial = signedTransactions.size() - (offset * pageSize) - 1;
        int limit = Math.max(initial - pageSize, 0);
        for(int i=initial; i >= limit; i--){
            try {
                CoreTransaction coreTransaction = signedTransactions.get(i).getCoreTransaction();
                TransactionList.TransactionData transactionData = new TransactionList.TransactionData();

                transactionData.setSigners(getSignersFromTx(signedTransactions.get(i)));

                if (coreTransaction.getInputs().size() > 0) {
                    transactionData.setInputs(getInputsFromTx(coreTransaction));
                    addInputTypeAndCount(transactionData);
                }

                transactionData.setOutputs(getOutputsFromTx(coreTransaction));
                transactionData.setOutputTypes(getOutputTypeAndCount(coreTransaction));

                transactionData.setTransactionId(coreTransaction.getId().toString());
                transactionData.setCommands(((WireTransaction) coreTransaction).getCommands().stream().map(command ->
                        command.getValue().getClass().getCanonicalName().substring(command.getValue().getClass()
                                .getCanonicalName().lastIndexOf(".") + 1)).collect(Collectors.toList()));

                transactionData.setNotary(coreTransaction.getNotary().getName().getOrganisation());
                transactionDataList.add(transactionData);
            }catch (Exception e){
                if(e.getClass().equals(TransactionDeserialisationException.class)){
                    throw new GenericException("Could not deserialize transaction. " +
                            "Make sure you have the cordapp directory set in the Settings Tab");
                }
            }
        }
        transactionList.setTransactionData(transactionDataList);
        return transactionList;
    }

    private List<TransactionList.Signer> getSignersFromTx(SignedTransaction signedTransaction){
        List<TransactionList.Signer> signerList = new ArrayList<>();
        signedTransaction.getSigs().forEach(signature -> {
            String key = CryptoUtils.toStringShort(signature.getBy());
            TransactionList.Signer signer = new TransactionList.Signer();
            signer.setSignature(signature);
            signer.setPartyName(explorerService.getPartyKeyMap().get(key));
            signerList.add(signer);
        });
        return signerList;
    }

    @SuppressWarnings("deprecation")
    private List<TransactionList.StateAndType> getInputsFromTx(CoreTransaction coreTransaction){
        List<TransactionList.StateAndType> inputList = new ArrayList<>();
        for (StateRef stateRef : coreTransaction.getInputs()) {
            SignedTransaction signedTransaction = NodeRPCClient.getRpcProxy()
                    .internalFindVerifiedTransaction(stateRef.getTxhash());
            if (signedTransaction != null) {
                inputList.add(new TransactionList.StateAndType(
                        signedTransaction.getCoreTransaction().getOutputStates().get(stateRef.getIndex()),
                        signedTransaction.getCoreTransaction().getOutputStates().get(stateRef.getIndex())
                                .getClass().getCanonicalName(), stateRef));
            }
        }
        return inputList;
    }

    private List<TransactionList.StateAndType> getOutputsFromTx(CoreTransaction coreTransaction){
        List<TransactionList.StateAndType> outputList = new ArrayList<>();
        AtomicReference<Integer> counter = new AtomicReference<>(0);
        coreTransaction.getOutputStates().forEach(contractState -> {
            outputList.add(new TransactionList.StateAndType(
                    contractState,
                    contractState.getClass().getCanonicalName(),
                    new StateRef(coreTransaction.getId(), counter.get()))
            );
            counter.getAndSet(counter.get() + 1);
        });
        return outputList;
    }

    private void addInputTypeAndCount(TransactionList.TransactionData transactionData){
        Map<String, Integer> inputTypeMap = new HashMap<>();
        transactionData.getInputs().forEach(stateAndType -> {
            String type = stateAndType.getState().getClass().toString().substring(
                    stateAndType.getState().getClass().toString().lastIndexOf(".") + 1);
            if (inputTypeMap.containsKey(type)) {
                inputTypeMap.put(type, inputTypeMap.get(type) + 1);
            } else {
                inputTypeMap.put(type, 1);
            }
        });

        List<TransactionList.TypeCount> inputTypeCountList = new ArrayList<>();
        inputTypeMap.keySet().forEach(s -> {
            TransactionList.TypeCount typeCount = new TransactionList.TypeCount();
            typeCount.setType(s);
            typeCount.setCount(inputTypeMap.get(s));
            inputTypeCountList.add(typeCount);
        });

        transactionData.setInputTypes(inputTypeCountList);
    }

    private List<TransactionList.TypeCount> getOutputTypeAndCount(CoreTransaction coreTransaction){
        Map<String, Integer> outputTypeMap = new HashMap<>();
        coreTransaction.getOutputStates().forEach(contractState -> {
            String type = contractState.getClass().toString().substring(
                    contractState.getClass().toString().lastIndexOf(".") + 1);
            if (outputTypeMap.containsKey(type)) {
                outputTypeMap.put(type, outputTypeMap.get(type) + 1);
            } else {
                outputTypeMap.put(type, 1);
            }
        });
        List<TransactionList.TypeCount> outputTypeCountList = new ArrayList<>();
        outputTypeMap.keySet().forEach(s -> {
            TransactionList.TypeCount typeCount = new TransactionList.TypeCount();
            typeCount.setType(s);
            typeCount.setCount(outputTypeMap.get(s));
            outputTypeCountList.add(typeCount);
        });
        return outputTypeCountList;
    }
    @Override
    public Object triggerFlow(FlowInfo flowInfo) throws UnsupportedFlowParamException,
            ClassNotFoundException, ExecutionException, InterruptedException {
        Class clazz = Class.forName(flowInfo.getFlowName());
        List<Object> params = new ArrayList<>();
        if(flowInfo.getFlowParams() != null && flowInfo.getFlowParams().size()>0) {
            for (FlowParam flowParam : flowInfo.getFlowParams()) {
                params.add(buildFlowParam(flowParam));
            }
        }
        if(params.size() == 0){
            return NodeRPCClient.getRpcProxy().startFlowDynamic(clazz).getReturnValue().get();
        }else {
            return NodeRPCClient.getRpcProxy().startFlowDynamic(clazz, params.toArray()).getReturnValue().get();
        }
    }

    private Object buildFlowParam(FlowParam flowParam){
        if(flowParam.getFlowParams()!=null && flowParam.getFlowParams().size()>0){
            List<Object> params = new ArrayList<>();
            Class clazz = null;
            try {
                clazz = Class.forName(flowParam.getParamType().getCanonicalName());
            }catch (ClassNotFoundException e){
                clazz = loadClassFromCordappJar(flowParam.getParamType());
                if(clazz == null){
                    throw new GenericException("Cannot load flow class "+ flowParam.getParamType().toString());
                }
            }

            try {
                for (FlowParam param : flowParam.getFlowParams()) {
                    params.add(buildFlowParam(param));
                }

                for (Constructor<?> ctor : clazz.getConstructors()) {
                    if (ctor.getParameters().length == params.size())
                        return ctor.newInstance(params.toArray());
                }
            }catch (Exception e){
                throw new GenericException(e.getMessage());
            }
        }

        switch (flowParam.getParamType().getCanonicalName()){
            case "net.corda.core.identity.Party":
                return NodeRPCClient.getRpcProxy().partiesFromName((String)flowParam.getParamValue(), false).iterator().next();

            case "java.lang.String":
            case "java.lang.StringBuilder":
            case "java.lang.StringBuffer":
                return flowParam.getParamValue().toString();

            case "java.lang.Long":
            case "long":
                return Long.valueOf(flowParam.getParamValue().toString());

            case "java.lang.Integer":
            case "int":
                return Integer.valueOf(flowParam.getParamValue().toString());

            case "java.land.Double":
            case "double":
                return Double.valueOf(flowParam.getParamValue().toString());

            case "java.lang.Float":
            case "float":
                return Float.valueOf(flowParam.getParamValue().toString());

            case "java.math.BigDecimal":
                return new BigDecimal(flowParam.getParamValue().toString());

            case "java.math.BigInteger":
                return new BigInteger(flowParam.getParamValue().toString());

            case "java.lang.Boolean":
            case "boolean":
                return Boolean.valueOf(flowParam.getParamValue().toString());

            case "java.util.UUID":
                return UUID.fromString(flowParam.getParamValue().toString());

            case "net.corda.core.contracts.UniqueIdentifier":
                return new UniqueIdentifier(null, UUID.fromString(flowParam.getParamValue().toString()));

            case "net.corda.core.contracts.Amount":
                return Amount.parseCurrency(flowParam.getParamValue().toString());

            case "java.time.LocalDateTime":
                return LocalDateTime.parse(flowParam.getParamValue().toString());

            case "java.time.Instant":
                return LocalDateTime.parse(flowParam.getParamValue().toString()).atZone(ZoneId.systemDefault());

            case "java.time.LocalDate":
                return LocalDate.parse(flowParam.getParamValue().toString());

            case "net.corda.core.crypto.SecureHash":
                return SecureHash.parse(flowParam.getParamValue().toString());

            case "net.corda.core.utilities.OpaqueBytes":
                return OpaqueBytes.of(flowParam.getParamValue().toString().getBytes());

            case "java.util.List":
            case "java.util.Set":
                return buildListParam(flowParam);

            default:
                throw new UnsupportedFlowParamException("Type "+ flowParam.getParamType() + " in Flow Parameter not " +
                        "supported by current version of Node Explorer");
        }
    }

    private Object buildListParam(FlowParam flowParam){
        List<Object> paramVal = new ArrayList<>();
        ArrayList<Object> paramValArr = (ArrayList<Object>) flowParam.getParamValue();
        for(Object paramObj: paramValArr){
            FlowParam param = new FlowParam();
            param.setParamType(flowParam.getParameterizedType());
            param.setParamValue(paramObj);
            param.setFlowParams(flowParam.getFlowParams());
            //param.set
            Object val = buildFlowParam(param);
            paramVal.add(val);
        }
        return paramVal;
    }

    private List<File> loadCorDappsToClassPath() {
        //TODO Fetch from Config
        String CORDAPP_PATH = settingsService.getApplicationSettings().getCordappDirectory();

        List<File> jarFiles = filterJarFiles(CORDAPP_PATH);
        addJarFilesToClassPath(jarFiles);
        return jarFiles;
    }

    private List<File> filterJarFiles(String path){
        File dir = new File(path);
        List<File> jarFiles = new ArrayList<>();
        File[] filesList = dir.listFiles();
        if(filesList != null && filesList.length > 0) {
            Arrays.stream(filesList).filter(file ->
                    file.getName().contains(".jar"))
                    .forEach(jarFiles::add);
        }
        return jarFiles;
    }

    private void addJarFilesToClassPath(List<File> jarFiles) {
        URLClassLoader sysClassLoader = (URLClassLoader) getClass().getClassLoader();
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            jarFiles.forEach(file -> {
                try {
                    URL url = file.toURI().toURL();
                    method.setAccessible(true);
                    method.invoke(sysClassLoader, url);
                } catch (IllegalAccessException | InvocationTargetException | MalformedURLException e) {
                    e.printStackTrace();
                }
            });
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private Class loadClassFromCordappJar(Class clazz){
        for(File jarFile: jarFiles){
            try{
                URL url = jarFile.toURI().toURL();
                URLClassLoader classLoader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
                try {
                    clazz = Class.forName(clazz.getName(), false, classLoader);
                    break;
                } catch (ClassNotFoundException e) {
                    try {
                        clazz = Class.forName(clazz.getCanonicalName(), false, classLoader);
                        break;
                    }catch (ClassNotFoundException ce){}
                }
            }catch (MalformedURLException e){
            }
        }
        return clazz;
    }

    private List<FlowInfo> loadFlowsInfoFromJarFiles(List<File> jarFiles, List<String> registeredFlows){
        List<FlowInfo> flowInfoList = new ArrayList<>();

        for(String flow: registeredFlows){
            if(!flow.contains("net.corda.core.flows") && !flow.contains("com.r3.corda.lib")){
                for(File jarFile: jarFiles){
                    try{
                        URL url = jarFile.toURI().toURL();
                        URLClassLoader classLoader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
                        FlowInfo flowInfo = new FlowInfo();
                        flowInfo.setFlowName(flow);
                        try {
                            Class flowClass = Class.forName(flow, true, classLoader);
                            flowInfo.setFlowParamsMap(loadFlowParams(flowClass));
                            flowInfoList.add(flowInfo);
                            break;
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }catch (MalformedURLException e){
                        e.printStackTrace();
                    }
                }
            }
        }
        return flowInfoList;
    }

    // TODO: Handle multiple constructors
    private Map<String, List<FlowParam>> loadFlowParams(Class flowClass) {
        List<FlowParam> flowParamList = new ArrayList<>();
        Map<String, List<FlowParam>> conMap = new LinkedHashMap<>();
        int counter = 1;
        // construct params List for each constructor

        for (Constructor constructor : flowClass.getConstructors()) {
            if(constructor.getParameters().length> 0 && constructor.getParameters()[0].isNamePresent()) {
                try {
                    flowParamList = collectObjectTypes(constructor.getParameters());
                    conMap.put("Constructor_" + counter, flowParamList);
                    counter++;
                } catch (ClassNotFoundException | UnsupportedFlowParamException cne) {
                    cne.printStackTrace();
                }
            }
        }

        return conMap;
    }

    private List<FlowParam> collectObjectTypes(Parameter[] parameters) throws ClassNotFoundException {
        List<FlowParam> flowParamList = new ArrayList<>();
        for(Parameter param: parameters) {
            if (param.isNamePresent()) {
                FlowParam flowParam = new FlowParam();
                flowParam.setParamName(param.getName());
                flowParam.setParamType(param.getType());

                if (param.getType().equals(List.class) || param.getType().equals(Set.class)) {
                    try {
                        Class type = Class.forName(((ParameterizedType) param.getParameterizedType()).getActualTypeArguments()[0].getTypeName());
                        flowParam.setParameterizedType(type);
                        flowParam.setHasParameterizedType(true);
                        removeKotlinDefaultConstructorAndCollectParam(type, flowParam);
                        // TODO brainstorm new approach for List of Complex object
//                        if(!typeList.contains(type.getCanonicalName())){
//                            FlowParam.ParamListWrapper paramList = new FlowParam.ParamListWrapper(flowParam.getFlowParams());
//                            List<FlowParam.ParamListWrapper> list = new ArrayList<>();
//                            list.add(paramList);
//                            flowParam.setParamValue(list);
//                        }
                    } catch (ClassNotFoundException cne){
                       throw new UnsupportedFlowParamException("Flow param unsupported by Node Explorer see https://github.com/corda/node-explorer/releases");
                    }
                }
                else
                    removeKotlinDefaultConstructorAndCollectParam(param.getType(), flowParam);

                flowParamList.add(flowParam);
            }
        }
        return flowParamList;
    }

    private void removeKotlinDefaultConstructorAndCollectParam(Class type, FlowParam flowParam) throws ClassNotFoundException {
        if(!typeList.contains(type.getCanonicalName())){
            for (int i = 0; i < type.getConstructors().length; i++) {
                boolean collected = false;
                if(type.getConstructors()[i].getParameters().length > 0 &&
                        type.getConstructors()[i].getParameters()[0].isNamePresent() && !collected) {
                    flowParam.setFlowParams(
                            collectObjectTypes(type.getConstructors()[0].getParameters()));
                    collected = true;
                }else{
                    continue;
                }
            }
        }
    }
}