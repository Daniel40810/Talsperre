-- Talsperre: entfernt alle TAL_-Tabellen im Schema DEMO (samt Spatial-Indizes und Metadaten).
-- Anweisungen sind durch eine Zeile mit nur "/" getrennt.
BEGIN
  FOR t IN (SELECT table_name FROM user_tables WHERE table_name LIKE 'TAL\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP TABLE "' || t.table_name || '" CASCADE CONSTRAINTS PURGE';
  END LOOP;
  DELETE FROM user_sdo_geom_metadata WHERE table_name LIKE 'TAL\_%' ESCAPE '\';
  COMMIT;
END;
/
