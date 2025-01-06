DELETE FROM top_strikes_ticker_expiration tste
    USING ticker_expirations te, ticker t
WHERE tste.ticker_symbol = te.ticker_symbol
  AND tste.expiration_date = te.expiration_date
  AND tste.ticker_symbol = t.ticker_symbol
  AND (tste.strike_price < (t.current_price - te.expected_move_dollars)
    OR tste.strike_price > (t.current_price + te.expected_move_dollars));
