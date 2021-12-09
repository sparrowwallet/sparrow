package com.sparrowwallet.sparrow.io;

import com.google.gson.*;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.Mode;
import com.sparrowwallet.sparrow.Theme;
import com.sparrowwallet.sparrow.net.*;
import com.sparrowwallet.sparrow.wallet.FeeRatesSelection;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.net.PagedBatchRequestBuilder.DEFAULT_PAGE_SIZE;
import static com.sparrowwallet.sparrow.net.TcpTransport.DEFAULT_MAX_TIMEOUT;

public class Config {
    private static final Logger log = LoggerFactory.getLogger(Config.class);

    public static final String CONFIG_FILENAME = "config";

    private Mode mode;
    private BitcoinUnit bitcoinUnit;
    private FeeRatesSource feeRatesSource;
    private FeeRatesSelection feeRatesSelection;
    private OptimizationStrategy sendOptimizationStrategy;
    private Currency fiatCurrency;
    private ExchangeSource exchangeSource;
    private boolean loadRecentWallets = true;
    private boolean validateDerivationPaths = true;
    private boolean groupByAddress = true;
    private boolean includeMempoolOutputs = true;
    private boolean notifyNewTransactions = true;
    private boolean checkNewVersions = true;
    private Theme theme;
    private boolean openWalletsInNewWindows = false;
    private boolean hideEmptyUsedAddresses = false;
    private boolean showTransactionHex = true;
    private boolean showLoadingLog = true;
    private boolean preventSleep = false;
    private List<File> recentWalletFiles;
    private Integer keyDerivationPeriod;
    private File hwi;
    private Boolean hdCapture;
    private String webcamDevice;
    private ServerType serverType;
    private String publicElectrumServer;
    private String coreServer;
    private List<String> recentCoreServers;
    private CoreAuthType coreAuthType;
    private File coreDataDir;
    private String coreAuth;
    private String electrumServer;
    private List<String> recentElectrumServers;
    private File electrumServerCert;
    private boolean useProxy;
    private String proxyServer;
    private int maxServerTimeout = DEFAULT_MAX_TIMEOUT;
    private int batchPageSize = DEFAULT_PAGE_SIZE;
    private boolean usePayNym;
    private boolean sameAppMixing;
    private Double appWidth;
    private Double appHeight;

    private static Config INSTANCE;

    private static Gson getGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(File.class, new FileSerializer());
        gsonBuilder.registerTypeAdapter(File.class, new FileDeserializer());
        return gsonBuilder.setPrettyPrinting().disableHtmlEscaping().create();
    }

    private static File getConfigFile() {
        File sparrowDir = Storage.getSparrowDir();
        return new File(sparrowDir, CONFIG_FILENAME);
    }

    private static Config load() {
        File configFile = getConfigFile();
        if(configFile.exists()) {
            try {
                Reader reader = new FileReader(configFile);
                Config config = getGson().fromJson(reader, Config.class);
                reader.close();

                if(config != null) {
                    return config;
                }
            } catch(Exception e) {
                log.error("Error opening " + configFile.getAbsolutePath(), e);
                //Ignore and assume no config
            }
        }

        return new Config();
    }

    public static synchronized Config get() {
        if(INSTANCE == null) {
            INSTANCE = load();
        }

        return INSTANCE;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        flush();
    }

    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }

    public void setBitcoinUnit(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
        flush();
    }

    public FeeRatesSource getFeeRatesSource() {
        return feeRatesSource;
    }

    public void setFeeRatesSource(FeeRatesSource feeRatesSource) {
        this.feeRatesSource = feeRatesSource;
        flush();
    }

    public FeeRatesSelection getFeeRatesSelection() {
        return feeRatesSelection;
    }

    public void setFeeRatesSelection(FeeRatesSelection feeRatesSelection) {
        this.feeRatesSelection = feeRatesSelection;
        flush();
    }

    public OptimizationStrategy getSendOptimizationStrategy() {
        return sendOptimizationStrategy;
    }

    public void setSendOptimizationStrategy(OptimizationStrategy sendOptimizationStrategy) {
        this.sendOptimizationStrategy = sendOptimizationStrategy;
        flush();
    }

    public Currency getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(Currency fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
        flush();
    }

    public boolean isFetchRates() {
        return getExchangeSource() != ExchangeSource.NONE;
    }

    public ExchangeSource getExchangeSource() {
        return exchangeSource;
    }

    public void setExchangeSource(ExchangeSource exchangeSource) {
        this.exchangeSource = exchangeSource;
        flush();
    }

    public boolean isLoadRecentWallets() {
        return loadRecentWallets;
    }

    public void setLoadRecentWallets(boolean loadRecentWallets) {
        this.loadRecentWallets = loadRecentWallets;
        flush();
    }

    public boolean isValidateDerivationPaths() {
        return validateDerivationPaths;
    }

    public void setValidateDerivationPaths(boolean validateDerivationPaths) {
        this.validateDerivationPaths = validateDerivationPaths;
        flush();
    }

    public boolean isGroupByAddress() {
        return groupByAddress;
    }

    public void setGroupByAddress(boolean groupByAddress) {
        this.groupByAddress = groupByAddress;
        flush();
    }

    public boolean isIncludeMempoolOutputs() {
        return includeMempoolOutputs;
    }

    public void setIncludeMempoolOutputs(boolean includeMempoolOutputs) {
        this.includeMempoolOutputs = includeMempoolOutputs;
        flush();
    }

    public boolean isNotifyNewTransactions() {
        return notifyNewTransactions;
    }

    public void setNotifyNewTransactions(boolean notifyNewTransactions) {
        this.notifyNewTransactions = notifyNewTransactions;
        flush();
    }

    public boolean isCheckNewVersions() {
        return checkNewVersions;
    }

    public void setCheckNewVersions(boolean checkNewVersions) {
        this.checkNewVersions = checkNewVersions;
        flush();
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
        flush();
    }

    public boolean isOpenWalletsInNewWindows() {
        return openWalletsInNewWindows;
    }

    public void setOpenWalletsInNewWindows(boolean openWalletsInNewWindows) {
        this.openWalletsInNewWindows = openWalletsInNewWindows;
        flush();
    }

    public boolean isHideEmptyUsedAddresses() {
        return hideEmptyUsedAddresses;
    }

    public void setHideEmptyUsedAddresses(boolean hideEmptyUsedAddresses) {
        this.hideEmptyUsedAddresses = hideEmptyUsedAddresses;
        flush();
    }

    public boolean isShowTransactionHex() {
        return showTransactionHex;
    }

    public void setShowTransactionHex(boolean showTransactionHex) {
        this.showTransactionHex = showTransactionHex;
        flush();
    }

    public boolean isShowLoadingLog() {
        return showLoadingLog;
    }

    public void setShowLoadingLog(boolean showLoadingLog) {
        this.showLoadingLog = showLoadingLog;
        flush();
    }

    public boolean isPreventSleep() {
        return preventSleep;
    }

    public void setPreventSleep(boolean preventSleep) {
        this.preventSleep = preventSleep;
    }

    public List<File> getRecentWalletFiles() {
        return recentWalletFiles;
    }

    public void setRecentWalletFiles(List<File> recentWalletFiles) {
        this.recentWalletFiles = recentWalletFiles;
        flush();
    }

    public Integer getKeyDerivationPeriod() {
        return keyDerivationPeriod;
    }

    public void setKeyDerivationPeriod(Integer keyDerivationPeriod) {
        this.keyDerivationPeriod = keyDerivationPeriod;
        flush();
    }

    public File getHwi() {
        return hwi;
    }

    public void setHwi(File hwi) {
        this.hwi = hwi;
        flush();
    }

    public Boolean getHdCapture() {
        return hdCapture;
    }

    public Boolean isHdCapture() {
        return hdCapture != null && hdCapture;
    }

    public void setHdCapture(Boolean hdCapture) {
        this.hdCapture = hdCapture;
        flush();
    }

    public String getWebcamDevice() {
        return webcamDevice;
    }

    public void setWebcamDevice(String webcamDevice) {
        this.webcamDevice = webcamDevice;
        flush();
    }

    public ServerType getServerType() {
        return serverType;
    }

    public void setServerType(ServerType serverType) {
        this.serverType = serverType;
        flush();
    }

    public boolean hasServerAddress() {
        return getServerAddress() != null && !getServerAddress().isEmpty();
    }

    public String getServerAddress() {
        return getServerType() == ServerType.BITCOIN_CORE ? getCoreServer() : (getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER ? getPublicElectrumServer() : getElectrumServer());
    }

    public boolean requiresInternalTor() {
        if(isUseProxy()) {
            return false;
        }

        return requiresTor();
    }

    public boolean requiresTor() {
        if(!hasServerAddress()) {
            return false;
        }

        Protocol protocol = Protocol.getProtocol(getServerAddress());
        if(protocol == null) {
            return false;
        }

        return protocol.isOnionAddress(protocol.getServerHostAndPort(getServerAddress()));
    }

    public String getPublicElectrumServer() {
        return publicElectrumServer;
    }

    public void setPublicElectrumServer(String publicElectrumServer) {
        this.publicElectrumServer = publicElectrumServer;
        flush();
    }

    public void changePublicServer() {
        List<String> otherServers = PublicElectrumServer.getServers().stream().map(PublicElectrumServer::getUrl).filter(url -> !url.equals(getPublicElectrumServer())).collect(Collectors.toList());
        if(!otherServers.isEmpty()) {
            setPublicElectrumServer(otherServers.get(new Random().nextInt(otherServers.size())));
        }
    }

    public String getCoreServer() {
        return coreServer;
    }

    public void setCoreServer(String coreServer) {
        this.coreServer = coreServer;
        flush();
    }

    public List<String> getRecentCoreServers() {
        return recentCoreServers;
    }

    public void addRecentCoreServer(String coreServer) {
        if(recentCoreServers == null) {
            recentCoreServers = new ArrayList<>();
        }

        if(!recentCoreServers.contains(coreServer)) {
            recentCoreServers.stream().filter(url -> Objects.equals(Protocol.getHost(url), Protocol.getHost(coreServer)))
                    .findFirst().ifPresent(existingUrl -> recentCoreServers.remove(existingUrl));
            recentCoreServers.add(coreServer);
            flush();
        }
    }

    public CoreAuthType getCoreAuthType() {
        return coreAuthType;
    }

    public void setCoreAuthType(CoreAuthType coreAuthType) {
        this.coreAuthType = coreAuthType;
        flush();
    }

    public File getCoreDataDir() {
        return coreDataDir;
    }

    public void setCoreDataDir(File coreDataDir) {
        this.coreDataDir = coreDataDir;
        flush();
    }

    public String getCoreAuth() {
        return coreAuth;
    }

    public void setCoreAuth(String coreAuth) {
        this.coreAuth = coreAuth;
        flush();
    }

    public String getElectrumServer() {
        return electrumServer;
    }

    public void setElectrumServer(String electrumServer) {
        this.electrumServer = electrumServer;
        flush();
    }

    public List<String> getRecentElectrumServers() {
        return recentElectrumServers;
    }

    public void addRecentServer() {
        if(serverType == ServerType.BITCOIN_CORE && coreServer != null) {
            addRecentCoreServer(coreServer);
        } else if(serverType == ServerType.ELECTRUM_SERVER && electrumServer != null) {
            addRecentElectrumServer(electrumServer);
        }
    }

    public void addRecentElectrumServer(String electrumServer) {
        if(recentElectrumServers == null) {
            recentElectrumServers = new ArrayList<>();
        }

        if(!recentElectrumServers.contains(electrumServer)) {
            recentElectrumServers.stream().filter(url -> Objects.equals(Protocol.getHost(url), Protocol.getHost(electrumServer)))
                    .findFirst().ifPresent(existingUrl -> recentElectrumServers.remove(existingUrl));
            recentElectrumServers.add(electrumServer);
            flush();
        }
    }

    public File getElectrumServerCert() {
        return electrumServerCert;
    }

    public void setElectrumServerCert(File electrumServerCert) {
        this.electrumServerCert = electrumServerCert;
        flush();
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
        flush();
    }

    public String getProxyServer() {
        return proxyServer;
    }

    public void setProxyServer(String proxyServer) {
        this.proxyServer = proxyServer;
        flush();
    }

    public int getMaxServerTimeout() {
        return maxServerTimeout;
    }

    public int getBatchPageSize() {
        return batchPageSize;
    }

    public boolean isUsePayNym() {
        return usePayNym;
    }

    public void setUsePayNym(boolean usePayNym) {
        this.usePayNym = usePayNym;
        flush();
    }

    public boolean isSameAppMixing() {
        return sameAppMixing;
    }

    public void setSameAppMixing(boolean sameAppMixing) {
        this.sameAppMixing = sameAppMixing;
        flush();
    }

    public Double getAppWidth() {
        return appWidth;
    }

    public void setAppWidth(Double appWidth) {
        this.appWidth = appWidth;
        flush();
    }

    public Double getAppHeight() {
        return appHeight;
    }

    public void setAppHeight(Double appHeight) {
        this.appHeight = appHeight;
        flush();
    }

    private synchronized void flush() {
        Gson gson = getGson();
        try {
            File configFile = getConfigFile();
            if(!configFile.exists()) {
                Storage.createOwnerOnlyFile(configFile);
            }

            Writer writer = new FileWriter(configFile);
            gson.toJson(this, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            //Ignore
        }
    }

    private static class FileSerializer implements JsonSerializer<File> {
        @Override
        public JsonElement serialize(File src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getAbsolutePath());
        }
    }

    private static class FileDeserializer implements JsonDeserializer<File> {
        @Override
        public File deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new File(json.getAsJsonPrimitive().getAsString());
        }
    }
}
