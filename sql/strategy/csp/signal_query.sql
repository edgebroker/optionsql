WITH filtered_options AS (
    SELECT
        oc.ticker_symbol,
        t.current_price AS underlying_price,
        oc.expiration_date,
        oc.strike_price,
        oc.put_break_even AS break_even,
        oc.put_ror AS ror,
        oc.put_bpr AS bpr,
        oc.put_pop AS pop,
        oc.put_pot AS pot,
        oc.put_bid AS bid,
        oc.put_ask AS ask,
        oc.put_mid AS mid,
        oc.put_spread AS spread,              -- Use put_spread directly
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
        oc.put_vega_exp AS vega_exp,
        oc.put_theta_decay_exp AS theta_decay_exp,
        oc.put_elasticity AS elasticity,
        oc.put_dvr AS dvr,
        oc.put_ovr AS ovr,
        oc.skew,
        oc.skew_delta,
        ste.sentiment,
        ste.put_call_ratio AS pcr,
        oc.tte
    FROM optionchains oc
             LEFT JOIN ticker t ON oc.ticker_symbol = t.ticker_symbol
             LEFT JOIN sentiment_ticker_expiration ste
                       ON oc.ticker_symbol = ste.ticker_symbol
                           AND oc.expiration_date = ste.expiration_date
    WHERE oc.ticker_symbol = ?
      AND oc.expiration_date = ?
      AND ste.sentiment = 'Strong Bullish'
      AND oc.put_delta BETWEEN -0.25 AND -0.10
      AND oc.put_ivr >= 40.0
      AND ste.put_call_ratio BETWEEN 0.2 AND 0.8
      AND oc.put_ovr >= 0.05
      AND oc.put_oi >= 300                    -- Solid liquidity
      AND oc.put_volume >= 100               -- Active contract
      AND (
        (oc.put_oi >= 1000 AND oc.put_spread <= 0.10)  -- High OI → Tight spread
            OR (oc.put_oi BETWEEN 500 AND 999 AND oc.put_spread <= 0.20)  -- Medium OI → Moderate spread
            OR (oc.put_oi < 500 AND oc.put_spread <= 0.30)  -- Low OI → Wider spread
        )
      AND oc.put_dvr BETWEEN -1.0 AND 1.0    -- Balanced delta/vega
      AND oc.put_gex >= 0                    -- Stable gamma exposure
      AND oc.skew BETWEEN -0.05 AND 0.05     -- Controlled IV skew
      AND oc.skew_delta BETWEEN -0.10 AND 0.10 -- Balanced delta skew
      AND oc.put_elasticity BETWEEN -0.25 AND 0.50  -- Balanced price sensitivity
      AND oc.put_ror >= 2.5
      AND oc.put_bpr <= 25000
)
SELECT COALESCE(
               json_agg(json_build_object(
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
                       'spread', spread,    -- Included put_spread in JSON
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
                       'skew', skew,
                       'skew_delta', skew_delta,
                       'sentiment', sentiment,
                       'pcr', pcr,
                       'tte', tte
                        )), '[]'::json) AS signals
FROM filtered_options;
