WITH option_metrics AS (
    SELECT
        o.ticker_symbol,
        o.expiration_date,
        o.strike_price,
        o.side,
        o.iv,
        t.iv_historical_low,
        t.iv_historical_high,
        LEAST(((o.iv - t.iv_historical_low) / NULLIF(t.iv_historical_high - t.iv_historical_low, 0)) * 100, 999999.9999) AS ivr
    FROM
        optionchains o
            JOIN
        ticker t
        ON
            o.ticker_symbol = t.ticker_symbol
)
UPDATE optionchains AS o
SET ivr = om.ivr
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price
  AND o.side = om.side;
