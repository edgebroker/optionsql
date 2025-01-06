DELETE FROM zero_gamma_ticker_expiration zgte
    USING ticker_expirations te, ticker t
WHERE zgte.ticker_symbol = te.ticker_symbol
  AND zgte.expiration_date = te.expiration_date
  AND zgte.ticker_symbol = t.ticker_symbol
  AND (zgte.zero_gamma_level < (t.current_price - te.expected_move_dollars)
    OR zgte.zero_gamma_level > (t.current_price + te.expected_move_dollars));
