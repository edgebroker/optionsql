WITH option_metrics AS (
    SELECT
        ticker_symbol,
        expiration_date,
        strike_price,
        side,

        -- Implied Move
        iv * SQRT((expiration_date::DATE - CURRENT_DATE)::DOUBLE PRECISION / 365) AS implied_move,

        -- Call/Put IV Skew
        CASE
            WHEN side = 'Call' THEN
                iv / (SELECT iv FROM optionchains AS o2
                      WHERE o2.side = 'Put'
                        AND o2.strike_price = o1.strike_price
                        AND o2.expiration_date = o1.expiration_date
                        AND o2.ticker_symbol = o1.ticker_symbol)
            WHEN side = 'Put' THEN
                iv / (SELECT iv FROM optionchains AS o2
                      WHERE o2.side = 'Call'
                        AND o2.strike_price = o1.strike_price
                        AND o2.expiration_date = o1.expiration_date
                        AND o2.ticker_symbol = o1.ticker_symbol)
            ELSE NULL
            END AS skew,

        -- Call/Put Delta Skew
        CASE
            WHEN side = 'Call' THEN
                delta - (SELECT delta FROM optionchains AS o2
                         WHERE o2.side = 'Put'
                           AND o2.strike_price = o1.strike_price
                           AND o2.expiration_date = o1.expiration_date
                           AND o2.ticker_symbol = o1.ticker_symbol)
            WHEN side = 'Put' THEN
                delta - (SELECT delta FROM optionchains AS o2
                         WHERE o2.side = 'Call'
                           AND o2.strike_price = o1.strike_price
                           AND o2.expiration_date = o1.expiration_date
                           AND o2.ticker_symbol = o1.ticker_symbol)
            ELSE NULL
            END AS skew_delta,

        -- Percentile Rank of IV
        PERCENT_RANK() OVER (PARTITION BY ticker_symbol, expiration_date ORDER BY iv) AS percentile_rank_iv,

        -- Percentile Rank of Delta
            PERCENT_RANK() OVER (PARTITION BY ticker_symbol, expiration_date ORDER BY ABS(delta)) AS percentile_rank_delta,

        -- Probability of Profit (POP)
            CASE
                WHEN side = 'Call' THEN CUME_DIST() OVER (ORDER BY delta)
                WHEN side = 'Put' THEN 1 - CUME_DIST() OVER (ORDER BY delta)
                ELSE NULL
                END AS probability_of_profit,

        -- Probability of Touch (POT)
        2 * CUME_DIST() OVER (ORDER BY delta) - 1 AS probability_of_touch,

        -- Expected Move Percentage
            (iv * SQRT((expiration_date::DATE - CURRENT_DATE)::DOUBLE PRECISION / 365)) / ((bid + ask) / 2) * 100 AS expected_move_percentage,

        -- Time to Expiration (TTE)
        (expiration_date::DATE - CURRENT_DATE)::NUMERIC / 365 AS time_to_expiration,

        -- Notional Value of OI
            strike_price * oi * 100 AS notional_value_oi

    FROM
        optionchains AS o1
)
UPDATE optionchains AS o
SET probability_of_profit = om.probability_of_profit
FROM option_metrics AS om
WHERE o.ticker_symbol = om.ticker_symbol
  AND o.expiration_date = om.expiration_date
  AND o.strike_price = om.strike_price
  AND o.side = om.side;