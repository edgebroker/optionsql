SELECT
    CASE
        WHEN oc.skew_delta BETWEEN at.min_skew_delta AND at.max_skew_delta THEN true
        ELSE false
        END AS passed,
    oc.skew_delta as option,
    at.min_skew_delta||' to '||at.max_skew_delta as threshold
FROM
    optionchains oc
        JOIN
    adaptive_thresholds at
    ON oc.ticker_symbol = at.ticker_symbol
        AND oc.expiration_date = at.expiration_date
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
