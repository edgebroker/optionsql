WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Probability of Profit (POP) for calls and puts
        (1 - call_delta + ABS(put_delta)) / 2 AS probability_of_profit
    FROM
        optionchains
)
UPDATE optionchains AS o
SET probability_of_profit = om.probability_of_profit
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
