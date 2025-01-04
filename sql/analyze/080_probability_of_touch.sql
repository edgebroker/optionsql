WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,

        -- Aggregated Probability of Touch (POT) across calls and puts
        GREATEST(0, LEAST(1, 2 * call_delta)) AS call_pot,
        GREATEST(0, LEAST(1, 2 * ABS(put_delta))) AS put_pot,
        -- Aggregate the probabilities
        (GREATEST(0, LEAST(1, 2 * call_delta)) + GREATEST(0, LEAST(1, 2 * ABS(put_delta)))) / 2 AS probability_of_touch
    FROM
        optionchains
)
UPDATE optionchains AS o
SET
    probability_of_touch = om.probability_of_touch
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price;
