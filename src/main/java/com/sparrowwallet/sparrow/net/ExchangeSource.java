package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.ExchangeRatesUpdatedEvent;
import com.sparrowwallet.tern.http.client.HttpResponseException;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public enum ExchangeSource {
    NONE("None", null) {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return Collections.emptyList();
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            return null;
        }

        @Override
        public Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end) {
            return Collections.emptyMap();
        }
    },
    COINBASE("Coinbase", "No historical rates") {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return getRates().data.rates.keySet().stream().filter(code -> isValidISO4217Code(code.toUpperCase(Locale.ROOT)))
                    .map(code -> Currency.getInstance(code.toUpperCase(Locale.ROOT))).collect(Collectors.toList());
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            String currencyCode = currency.getCurrencyCode();
            OptionalDouble optRate = getRates().data.rates.entrySet().stream().filter(rate -> currencyCode.equalsIgnoreCase(rate.getKey())).mapToDouble(Map.Entry::getValue).findFirst();
            if(optRate.isPresent()) {
                return optRate.getAsDouble();
            }

            return null;
        }

        private CoinbaseRates getRates() {
            String url = "https://api.coinbase.com/v2/exchange-rates?currency=BTC";

            if(log.isInfoEnabled()) {
                log.info("Requesting exchange rates from " + url);
            }

            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                return httpClientService.requestJson(url, CoinbaseRates.class, null);
            } catch (Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving currency rates", e);
                } else {
                    log.warn("Error retrieving currency rates (" + e.getMessage() + ")");
                }
                return new CoinbaseRates();
            }
        }

        @Override
        public Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end) {
            Map<Date, Double> historicalRates = new TreeMap<>();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            Instant currentInstant = start.toInstant();
            Instant endInstant = end.toInstant();

            while(currentInstant.isBefore(endInstant) || currentInstant.equals(endInstant)) {
                Date fromDate = Date.from(currentInstant.atZone(ZoneId.systemDefault()).toInstant());
                currentInstant = currentInstant.plus(300, ChronoUnit.DAYS);
                Date toDate = Date.from(currentInstant.atZone(ZoneId.systemDefault()).toInstant());
                toDate = toDate.after(end) ? end : toDate;

                String startTime = dateFormat.format(fromDate);
                String endTime = dateFormat.format(toDate);

                String url = "https://api.pro.coinbase.com/products/BTC-" + currency.getCurrencyCode() + "/candles?start=" + startTime + "T12:00:00&end=" + endTime + "T12:00:00&granularity=86400";

                if(log.isInfoEnabled()) {
                    log.info("Requesting historical exchange rates from " + url);
                }

                HttpClientService httpClientService = AppServices.getHttpClientService();
                try {
                    Number[][] coinbaseData = httpClientService.requestJson(url, Number[][].class, Map.of("User-Agent", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)", "Accept", "*/*"));
                    for(Number[] price : coinbaseData) {
                        Date date = new Date(price[0].longValue() * 1000);
                        historicalRates.put(DateUtils.truncate(date, Calendar.DAY_OF_MONTH), price[4].doubleValue());
                    }
                } catch(Exception e) {
                    if(log.isDebugEnabled()) {
                        log.warn("Error retrieving historical currency rates", e);
                    } else {
                        if(e instanceof HttpResponseException httpException && httpException.getStatusCode() == 404) {
                            log.warn("Error retrieving historical currency rates (" + e.getMessage() + "). BTC-" + currency.getCurrencyCode() + " may not be supported by " + this);
                        } else {
                            log.warn("Error retrieving historical currency rates (" + e.getMessage() + ")");
                        }
                    }
                }
            }

            return historicalRates;
        }
    },
    COINGECKO("Coingecko", "No historical rates") {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return getRates().rates.entrySet().stream().filter(rate -> "fiat".equals(rate.getValue().type) && isValidISO4217Code(rate.getKey().toUpperCase(Locale.ROOT)))
                    .map(rate -> Currency.getInstance(rate.getKey().toUpperCase(Locale.ROOT))).collect(Collectors.toList());
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            String currencyCode = currency.getCurrencyCode();
            OptionalDouble optRate = getRates().rates.entrySet().stream().filter(rate -> currencyCode.equalsIgnoreCase(rate.getKey())).mapToDouble(rate -> rate.getValue().value).findFirst();
            if(optRate.isPresent()) {
                return optRate.getAsDouble();
            }

            return null;
        }

        private CoinGeckoRates getRates() {
            String url = "https://api.coingecko.com/api/v3/exchange_rates";

            if(log.isInfoEnabled()) {
                log.info("Requesting exchange rates from " + url);
            }

            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                return httpClientService.requestJson(url, CoinGeckoRates.class, null);
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving currency rates", e);
                } else {
                    log.warn("Error retrieving currency rates (" + e.getMessage() + ")");
                }
                return new CoinGeckoRates();
            }
        }

        @Override
        public Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end) {
            long startDate = start.getTime() / 1000;
            long endDate = end.getTime() / 1000;

            String url = "https://api.coingecko.com/api/v3/coins/bitcoin/market_chart/range?vs_currency=" + currency.getCurrencyCode() + "&from=" + startDate + "&to=" + endDate;

            if(log.isInfoEnabled()) {
                log.info("Requesting historical exchange rates from " + url);
            }

            Map<Date, Double> historicalRates = new TreeMap<>();
            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                CoinGeckoHistoricalRates coinGeckoHistoricalRates = httpClientService.requestJson(url, CoinGeckoHistoricalRates.class, null);
                for(List<Number> historicalRate : coinGeckoHistoricalRates.prices) {
                    Date date = new Date(historicalRate.get(0).longValue());
                    historicalRates.put(DateUtils.truncate(date, Calendar.DAY_OF_MONTH), historicalRate.get(1).doubleValue());
                }
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving historical currency rates", e);
                } else {
                    log.warn("Error retrieving historical currency rates (" + e.getMessage() + ")");
                }
            }

            return historicalRates;
        }
    },
    MEMPOOL_SPACE("mempool.space", "Historical rates from Apr 2023") {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return getRates().rates.entrySet().stream().filter(price -> isValidISO4217Code(price.getKey().toUpperCase(Locale.ROOT)))
                    .map(rate -> Currency.getInstance(rate.getKey().toUpperCase(Locale.ROOT))).collect(Collectors.toList());
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            String currencyCode = currency.getCurrencyCode();
            OptionalDouble optRate = getRates().rates.entrySet().stream().filter(price -> currencyCode.equalsIgnoreCase(price.getKey())).mapToDouble(Map.Entry::getValue).findFirst();
            if(optRate.isPresent()) {
                return optRate.getAsDouble();
            }

            return null;
        }

        private MempoolSpaceRates getRates() {
            String url = AppServices.isUsingProxy() ? "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/v1/prices" : "https://mempool.space/api/v1/prices";

            if(log.isInfoEnabled()) {
                log.info("Requesting exchange rates from " + url);
            }

            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                return httpClientService.requestJson(url, MempoolSpaceRates.class, null);
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving currency rates", e);
                } else {
                    log.warn("Error retrieving currency rates (" + e.getMessage() + ")");
                }
                return new MempoolSpaceRates();
            }
        }

        @Override
        public Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end) {
            String url = AppServices.isUsingProxy() ? "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/v1/historical-price?currency=" + currency.getCurrencyCode() :
                    "https://mempool.space/api/v1/historical-price?currency=" + currency.getCurrencyCode();

            if(log.isInfoEnabled()) {
                log.info("Requesting historical exchange rates from " + url);
            }

            Map<Date, Double> historicalRates = new TreeMap<>();
            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                MempoolSpaceHistoricalRates mempoolSpaceHistoricalRates = httpClientService.requestJson(url, MempoolSpaceHistoricalRates.class, null);
                Collections.reverse(mempoolSpaceHistoricalRates.prices); //Use "closing" rates
                for(MempoolSpaceRates historicalRate : mempoolSpaceHistoricalRates.prices) {
                    Date date = new Date(historicalRate.time * 1000);
                    if(date.after(start) && date.before(end) && historicalRate.rates.containsKey(currency.getCurrencyCode())) {
                        historicalRates.put(DateUtils.truncate(date, Calendar.DAY_OF_MONTH), historicalRate.rates.get(currency.getCurrencyCode()));
                    }
                }
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving historical currency rates", e);
                } else {
                    log.warn("Error retrieving historical currency rates (" + e.getMessage() + ")");
                }
            }

            return historicalRates;
        }
    };

    private static final Logger log = LoggerFactory.getLogger(ExchangeSource.class);

    private final String name;
    private final String description;

    ExchangeSource(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public abstract List<Currency> getSupportedCurrencies();

    public abstract Double getExchangeRate(Currency currency);

    public abstract Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end);

    private static boolean isValidISO4217Code(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }

    public static class CurrenciesService extends Service<List<Currency>> {
        private final ExchangeSource exchangeSource;

        public CurrenciesService(ExchangeSource exchangeSource) {
            this.exchangeSource = exchangeSource;
        }

        @Override
        protected Task<List<Currency>> createTask() {
            return new Task<>() {
                protected List<Currency> call() {
                    return exchangeSource.getSupportedCurrencies();
                }
            };
        }
    }

    public static class RatesService extends ScheduledService<ExchangeRatesUpdatedEvent> {
        private final ExchangeSource exchangeSource;
        private final Currency selectedCurrency;

        public RatesService(ExchangeSource exchangeSource, Currency selectedCurrency) {
            this.exchangeSource = exchangeSource;
            this.selectedCurrency = selectedCurrency;
        }

        protected Task<ExchangeRatesUpdatedEvent> createTask() {
            return new Task<>() {
                protected ExchangeRatesUpdatedEvent call() {
                    Double rate = exchangeSource.getExchangeRate(selectedCurrency);
                    return new ExchangeRatesUpdatedEvent(selectedCurrency, rate);
                }
            };
        }

        public ExchangeSource getExchangeSource() {
            return exchangeSource;
        }
    }

    private static class CoinbaseRates {
        public CoinbaseData data = new CoinbaseData();
    }

    private static class CoinbaseData {
        public String currency;
        public Map<String, Double> rates = new LinkedHashMap<>();
    }

    private static class CoinGeckoRates {
        public Map<String, CoinGeckoRate> rates = new LinkedHashMap<>();
    }

    private static class CoinGeckoRate {
        public String name;
        public String unit;
        public Double value;
        public String type;
    }

    private static class CoinGeckoHistoricalRates {
        public List<List<Number>> prices = new ArrayList<>();
    }

    private static class MempoolSpaceRates {
        public long time;
        public final Map<String, Double> rates = new LinkedHashMap<>();

        // Capture all other fields that Jackson do not match other members
        @JsonAnyGetter
        public Map<String, Double> getPrices() {
            return rates;
        }

        @JsonAnySetter
        public void setPrice(String name, Double value) {
            rates.put(name, value);
        }
    }

    private static class MempoolSpaceHistoricalRates {
        public List<MempoolSpaceRates> prices = new ArrayList<>();
    }
}
