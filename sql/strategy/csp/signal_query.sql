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
        oc.put_spread AS spread,
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
             LEFT JOIN sentiment_ticker_expiration ste ON oc.ticker_symbol = ste.ticker_symbol AND oc.expiration_date = ste.expiration_date
             LEFT JOIN adaptive_thresholds at ON oc.ticker_symbol = at.ticker_symbol AND oc.expiration_date = at.expiration_date
    WHERE oc.ticker_symbol = ?
      AND oc.expiration_date = ?
      AND oc.strike_price = ?
      AND oc.put_ivr >= at.high_ivr
      AND ste.put_call_ratio BETWEEN 0.2 AND at.avg_pcr
      AND oc.put_ovr >= 0.05
      AND oc.put_oi >= at.median_oi
      AND oc.put_volume >= 100
      AND oc.put_spread <= at.tight_spread
      AND oc.put_dvr BETWEEN (at.balanced_dvr - 0.05) AND (at.balanced_dvr + 0.05)
      AND oc.put_gex >= 0
      AND oc.skew <= at.controlled_skew
      AND oc.skew_delta BETWEEN -0.10 AND at.balanced_skew_delta
      AND oc.put_elasticity BETWEEN -0.25 AND at.balanced_elasticity
      AND oc.put_vega_exp BETWEEN -1000 AND at.high_vega_exp
      AND oc.put_theta_decay_exp <= at.minimal_theta_decay
      AND oc.put_ror >= 3.0
      AND oc.put_bpr <= at.avg_bpr
      AND oc.tte BETWEEN 30 AND 60
      AND (oc.put_vega_exp / NULLIF(ABS(oc.put_theta_decay_exp), 0)) <= 10
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
                       'spread', spread,
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
