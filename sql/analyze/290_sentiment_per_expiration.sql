WITH sentiment_calculations AS (
    SELECT
        oc.ticker_symbol,
        oc.expiration_date,
        SUM(oc.call_gex)                   AS call_gex_total,
        SUM(oc.put_gex)                    AS put_gex_total,
        SUM(oc.call_dex)                   AS call_dex_total,
        SUM(oc.put_dex)                    AS put_dex_total,

        -- GEX and DEX Differences
        SUM(oc.call_gex) - SUM(oc.put_gex) AS gex_difference,
        SUM(oc.call_dex) - SUM(oc.put_dex) AS dex_difference,

        -- Percentage Differences
        CASE
            WHEN SUM(oc.call_gex) + SUM(oc.put_gex) = 0 THEN 0
            ELSE ((SUM(oc.call_gex) - SUM(oc.put_gex)) / NULLIF(SUM(oc.call_gex) + SUM(oc.put_gex), 0)) * 100
            END AS gex_percentage_difference,

        CASE
            WHEN SUM(oc.call_dex) + SUM(oc.put_dex) = 0 THEN 0
            ELSE ((SUM(oc.call_dex) - SUM(oc.put_dex)) / NULLIF(SUM(oc.call_dex) + SUM(oc.put_dex), 0)) * 100
            END AS dex_percentage_difference,

        -- **Computed PCR (Put/Call Ratio) at expiration level**
        CASE
            WHEN SUM(oc.call_oi) = 0 THEN NULL
            ELSE SUM(oc.put_oi)::NUMERIC / NULLIF(SUM(oc.call_oi), 0)
            END AS put_call_ratio,

        -- 🚦 **Sentiment Determination**
        CASE
            WHEN ((SUM(oc.call_gex) - SUM(oc.put_gex)) / NULLIF(SUM(ABS(oc.call_gex) + ABS(oc.put_gex)), 0)) * 100 > 40
                AND ((SUM(oc.call_dex) - SUM(oc.put_dex)) / NULLIF(SUM(ABS(oc.call_dex) + ABS(oc.put_dex)), 0)) * 100 > 25
                AND SUM(oc.put_oi)::NUMERIC / NULLIF(SUM(oc.call_oi), 0) <= 0.6
                THEN 'Strong Bullish'

            WHEN ((SUM(oc.call_gex) - SUM(oc.put_gex)) / NULLIF(SUM(ABS(oc.call_gex) + ABS(oc.put_gex)), 0)) * 100 > 10
                AND ((SUM(oc.call_dex) - SUM(oc.put_dex)) / NULLIF(SUM(ABS(oc.call_dex) + ABS(oc.put_dex)), 0)) * 100 > 5
                AND SUM(oc.put_oi)::NUMERIC / NULLIF(SUM(oc.call_oi), 0) <= 0.8
                THEN 'Slightly Bullish'

            WHEN ABS(((SUM(oc.call_gex) - SUM(oc.put_gex)) / NULLIF(SUM(ABS(oc.call_gex) + ABS(oc.put_gex)), 0)) * 100) <= 5
                AND SUM(oc.put_oi)::NUMERIC / NULLIF(SUM(oc.call_oi), 0) BETWEEN 0.8 AND 1.2
                THEN 'Neutral'

            WHEN ((SUM(oc.put_gex) - SUM(oc.call_gex)) / NULLIF(SUM(ABS(oc.call_gex) + ABS(oc.put_gex)), 0)) * 100 > 10
                AND ((SUM(oc.put_dex) - SUM(oc.call_dex)) / NULLIF(SUM(ABS(oc.call_dex) + ABS(oc.put_dex)), 0)) * 100 > 5
                AND SUM(oc.put_oi)::NUMERIC / NULLIF(SUM(oc.call_oi), 0) >= 1.0
                THEN 'Slightly Bearish'

            ELSE 'Strong Bearish'
            END AS sentiment
    FROM optionchains oc
    GROUP BY oc.ticker_symbol, oc.expiration_date
)

--  **Insert Sentiment into sentiment_ticker_expiration**
INSERT INTO sentiment_ticker_expiration (
    ticker_symbol,
    expiration_date,
    sentiment,
    call_gex_total,
    put_gex_total,
    difference_percentage,
    put_call_ratio
)
SELECT
    sc.ticker_symbol,
    sc.expiration_date,
    sc.sentiment,
    sc.call_gex_total,
    sc.put_gex_total,
    sc.gex_percentage_difference,
    sc.put_call_ratio
FROM sentiment_calculations sc
ON CONFLICT (ticker_symbol, expiration_date)
    DO UPDATE SET
                  sentiment             = EXCLUDED.sentiment,
                  call_gex_total        = EXCLUDED.call_gex_total,
                  put_gex_total         = EXCLUDED.put_gex_total,
                  difference_percentage = EXCLUDED.difference_percentage,
                  put_call_ratio        = EXCLUDED.put_call_ratio;
