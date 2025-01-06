-- Step 1: Compute average metrics from the optionchains table
WITH avg_data AS (
    SELECT
        ticker_symbol,
        expiration_date,
        AVG(call_delta) FILTER (WHERE call_delta != 0.0) AS avg_call_delta,
        AVG(call_volume) FILTER (WHERE call_volume != 0) AS avg_call_volume,
        AVG(call_oi) FILTER (WHERE call_oi != 0) AS avg_call_oi,
        AVG(call_bid) FILTER (WHERE call_bid != 0.0) AS avg_call_bid,
        AVG(call_ask) FILTER (WHERE call_ask != 0.0) AS avg_call_ask,
        AVG(call_mid) FILTER (WHERE call_mid != 0.0) AS avg_call_mid,
        AVG(call_iv) FILTER (WHERE call_iv != 0.0) AS avg_call_iv,
        AVG(call_gex) FILTER (WHERE call_gex != 0.0) AS avg_call_gex,
        AVG(call_dex) FILTER (WHERE call_dex != 0.0) AS avg_call_dex,
        AVG(put_delta) FILTER (WHERE put_delta != 0.0) AS avg_put_delta,
        AVG(put_volume) FILTER (WHERE put_volume != 0) AS avg_put_volume,
        AVG(put_oi) FILTER (WHERE put_oi != 0) AS avg_put_oi,
        AVG(put_bid) FILTER (WHERE put_bid != 0.0) AS avg_put_bid,
        AVG(put_ask) FILTER (WHERE put_ask != 0.0) AS avg_put_ask,
        AVG(put_mid) FILTER (WHERE put_mid != 0.0) AS avg_put_mid,
        AVG(put_iv) FILTER (WHERE put_iv != 0.0) AS avg_put_iv,
        AVG(put_gex) FILTER (WHERE put_gex != 0.0) AS avg_put_gex,
        AVG(put_dex) FILTER (WHERE put_dex != 0.0) AS avg_put_dex
    FROM
        optionchains
    GROUP BY
        ticker_symbol, expiration_date
)

-- Step 2: Update the ticker_expirations table with the computed averages
UPDATE ticker_expirations te
SET
    avg_call_delta = avg_data.avg_call_delta,
    avg_call_volume = avg_data.avg_call_volume,
    avg_call_oi = avg_data.avg_call_oi,
    avg_call_bid = avg_data.avg_call_bid,
    avg_call_ask = avg_data.avg_call_ask,
    avg_call_mid = avg_data.avg_call_mid,
    avg_call_iv = avg_data.avg_call_iv,
    avg_call_gex = avg_data.avg_call_gex,
    avg_call_dex = avg_data.avg_call_dex,
    avg_put_delta = avg_data.avg_put_delta,
    avg_put_volume = avg_data.avg_put_volume,
    avg_put_oi = avg_data.avg_put_oi,
    avg_put_bid = avg_data.avg_put_bid,
    avg_put_ask = avg_data.avg_put_ask,
    avg_put_mid = avg_data.avg_put_mid,
    avg_put_iv = avg_data.avg_put_iv,
    avg_put_gex = avg_data.avg_put_gex,
    avg_put_dex = avg_data.avg_put_dex
FROM
    avg_data
WHERE
    te.ticker_symbol = avg_data.ticker_symbol
  AND te.expiration_date = avg_data.expiration_date;
