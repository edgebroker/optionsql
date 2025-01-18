WITH filtered_options AS (
    SELECT
        oc.ticker_symbol,
        t.current_price AS underlying_price,
        oc.expiration_date,
        oc.strike_price,
        oc.put_break_even AS break_even,
        ((oc.put_bid * 100) / (oc.strike_price * 100 - oc.put_bid * 100)) * 100 AS ror,
        (oc.strike_price * 100 - oc.put_bid * 100) AS bpr,
        oc.probability_of_profit AS pop,
        oc.probability_of_touch AS pot,
        oc.put_bid AS bid,
        oc.put_ask AS ask,
        oc.put_mid AS mid,
        oc.put_volume AS volume,
        oc.put_oi AS oi,
        oc.put_delta AS delta,
        oc.put_gamma AS gamma,
        oc.put_theta AS theta,
        oc.put_vega AS vega,
        oc.put_iv AS iv,
        oc.put_ivr AS ivr,
        oc.put_dex AS dex,
        oc.put_gex AS gex,
        oc.put_vega_exposure AS vega_exp,
        oc.put_theta_decay_exposure AS theta_decay_exp,
        oc.put_elasticity AS elasticity,
        oc.put_delta_vega_ratio AS dvr,
        oc.put_oi_volume_ratio AS ovr,
        ste.sentiment,
        ste.put_call_ratio AS pcr,
        oc.time_to_expiration AS tte
    FROM optionchains oc
             LEFT JOIN ticker t ON oc.ticker_symbol = t.ticker_symbol
             LEFT JOIN sentiment_ticker_expiration ste
                       ON oc.ticker_symbol = ste.ticker_symbol AND oc.expiration_date = ste.expiration_date
    WHERE oc.ticker_symbol = ?
      AND oc.expiration_date = ?
--      AND ste.sentiment = 'Strong Bullish'
      AND oc.put_delta BETWEEN -0.25 AND -0.10
      AND oc.put_ivr >= 40.0
--      AND oc.put_oi >= 300                -- Ensure solid put-side liquidity
--      AND (oc.call_oi + oc.put_oi) >= 600  -- Confirm overall market interest
--      AND (oc.put_ask - oc.put_bid) <= 0.10  -- Max $0.10 spread
      AND ste.put_call_ratio BETWEEN 0.2 AND 0.8
      AND oc.put_oi_volume_ratio >= 0.05
)
SELECT json_agg(json_build_object(
        'ticker_symbol', ticker_symbol,
        'underlying_price', underlying_price,
        'expiration_date', expiration_date,
        'strike_price', strike_price,
        'break_even', break_even,
        'ror', ror,
        'bpr', bpr,
        'pop', pop,
        'pot', pot,
        'bid', bid,
        'ask', ask,
        'mid', mid,
        'volume', volume,
        'oi', oi,
        'delta', delta,
        'gamma', gamma,
        'theta', theta,
        'vega', vega,
        'iv', iv,
        'ivr', ivr,
        'dex', dex,
        'gex', gex,
        'vega_exp', vega_exp,
        'theta_decay_exp', theta_decay_exp,
        'elasticity', elasticity,
        'dvr', dvr,
        'ovr', ovr,
        'sentiment', sentiment,
        'pcr', pcr,
        'tte', tte
    )) AS signals
FROM filtered_options
where ror >= 2.5
  AND bpr <= 25000
