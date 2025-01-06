WITH sentiment_calculations AS (SELECT oc.ticker_symbol,
                                       oc.expiration_date,
                                       SUM(oc.call_gex)                   AS call_gex_total,
                                       SUM(oc.put_gex)                    AS put_gex_total,
                                       -- Calculate difference and total GEX
                                       SUM(oc.call_gex) - SUM(oc.put_gex) AS difference,
                                       SUM(oc.call_gex) + SUM(oc.put_gex) AS total_gex,
                                       -- Calculate percentage difference
                                       CASE
                                           WHEN SUM(oc.call_gex) + SUM(oc.put_gex) = 0 THEN 0
                                           ELSE ((SUM(oc.call_gex) - SUM(oc.put_gex)) /
                                                 NULLIF(SUM(oc.call_gex) + SUM(oc.put_gex), 0)) * 100
                                           END                            AS percentage_difference,
                                       -- Determine sentiment based on percentage difference
                                       CASE
                                           WHEN ((SUM(oc.call_gex) - SUM(oc.put_gex)) /
                                                 NULLIF(SUM(oc.call_gex) + SUM(oc.put_gex), 0)) * 100 > 30
                                               THEN 'Strong Bullish'
                                           WHEN ((SUM(oc.call_gex) - SUM(oc.put_gex)) /
                                                 NULLIF(SUM(oc.call_gex) + SUM(oc.put_gex), 0)) * 100 > 5
                                               THEN 'Slightly Bullish'
                                           WHEN ((SUM(oc.call_gex) - SUM(oc.put_gex)) /
                                                 NULLIF(SUM(oc.call_gex) + SUM(oc.put_gex), 0)) * 100 >= -5
                                               THEN 'Neutral'
                                           WHEN ((SUM(oc.call_gex) - SUM(oc.put_gex)) /
                                                 NULLIF(SUM(oc.call_gex) + SUM(oc.put_gex), 0)) * 100 >= -30
                                               THEN 'Slightly Bearish'
                                           ELSE 'Strong Bearish'
                                           END                            AS sentiment
                                FROM optionchains oc
                                GROUP BY oc.ticker_symbol, oc.expiration_date)
INSERT
INTO sentiment_ticker_expiration (ticker_symbol,
                                  expiration_date,
                                  sentiment,
                                  call_gex_total,
                                  put_gex_total,
                                  difference_percentage,
                                  put_call_ratio)
SELECT sc.ticker_symbol,
       sc.expiration_date,
       sc.sentiment,
       sc.call_gex_total,
       sc.put_gex_total,
       sc.percentage_difference,
       CASE
           WHEN sc.put_gex_total = 0 THEN NULL
           ELSE sc.put_gex_total / NULLIF(sc.call_gex_total, 0)
           END AS put_call_ratio
FROM sentiment_calculations sc
ON CONFLICT (ticker_symbol, expiration_date)
    DO UPDATE SET sentiment             = EXCLUDED.sentiment,
                  call_gex_total        = EXCLUDED.call_gex_total,
                  put_gex_total         = EXCLUDED.put_gex_total,
                  difference_percentage = EXCLUDED.difference_percentage,
                  put_call_ratio        = EXCLUDED.put_call_ratio;
