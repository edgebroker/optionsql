WITH option_metrics AS (
    SELECT
        o.ticker_symbol,
        o.expiration_date,
        o.strike_price,
        t.iv_historical_low,
        t.iv_historical_high,

        -- IVR for Calls
        CASE
            WHEN t.iv_historical_high > t.iv_historical_low THEN
                LEAST(((o.call_iv - t.iv_historical_low) / NULLIF(t.iv_historical_high - t.iv_historical_low, 0)) * 100, 999999.9999)
            END AS call_ivr,

        -- IVR for Puts
        CASE
            WHEN t.iv_historical_high > t.iv_historical_low THEN
                LEAST(((o.put_iv - t.iv_historical_low) / NULLIF(t.iv_historical_high - t.iv_historical_low, 0)) * 100, 999999.9999)
            END AS put_ivr
    FROM
        optionchains o
            JOIN ticker t ON o.ticker_symbol = t.ticker_symbol
)
UPDATE optionchains AS o
SET
    call_ivr = om.call_ivr,
    put_ivr = om.put_ivr
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
