WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Call/Put IV Skew
        CASE
            WHEN COALESCE(put_iv, 0) != 0 THEN call_iv / put_iv
            ELSE NULL
            END AS skew
    FROM
        optionchains
)
UPDATE optionchains AS o
SET
    skew = om.skew
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
