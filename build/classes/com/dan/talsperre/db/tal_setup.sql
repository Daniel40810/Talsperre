-- Talsperre: Tabellen, Stammdaten und Spatial-Metadaten im Schema DEMO (Oracle 21c, PDB PDBORCL).
-- Alle Objekte beginnen mit TAL_. Anweisungen sind durch eine Zeile mit nur "/" getrennt.
-- Koordinaten: SRID 8307 (WGS84, Länge, Breite). Lage und Umrisse stammen aus dem Modell der Szene,
-- einfach umgerechnet (Norden oben, Ursprung auf der Kronenmitte bei 51,1830 N / 9,0650 E): Näherungen, keine Vermessung.
-- Fahrten und Standard-Blickpunkte füllt das Java-Setup (db.TalSetup) aus dem laufenden Modell.

-- ============================================================ Steckbrief
CREATE TABLE tal_bauwerk (
  id                 NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT tal_bauwerk_pk PRIMARY KEY,
  kennung            VARCHAR2(30)  NOT NULL CONSTRAINT tal_bauwerk_uk UNIQUE,
  name               VARCHAR2(80)  NOT NULL,
  art                VARCHAR2(12)  NOT NULL CONSTRAINT tal_bauwerk_art_ck CHECK (art IN ('SPERRMAUER','KRAFTWERK','SEE','BRUECKE','ORTSTEIL','TURM')),
  baujahr            NUMBER(4),
  hoehe_m            NUMBER(7,2),
  laenge_m           NUMBER(9,2),
  stauinhalt_mio_m3  NUMBER(8,3),
  bemerkung          VARCHAR2(400),
  geom               SDO_GEOMETRY
)
/
CREATE TABLE tal_ereignis (
  id           NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT tal_ereignis_pk PRIMARY KEY,
  jahr         NUMBER(4)     NOT NULL,
  datum        DATE,
  titel        VARCHAR2(120) NOT NULL,
  beschreibung VARCHAR2(600),
  bauwerk_id   NUMBER CONSTRAINT tal_ereignis_bw_fk REFERENCES tal_bauwerk(id) ON DELETE SET NULL
)
/
-- ============================================================ Pegel
CREATE TABLE tal_pegel_station (
  id         NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT tal_pegel_station_pk PRIMARY KEY,
  kennung    VARCHAR2(30) NOT NULL CONSTRAINT tal_pegel_station_uk UNIQUE,
  name       VARCHAR2(80) NOT NULL,
  bemerkung  VARCHAR2(300),
  geom       SDO_GEOMETRY
)
/
-- Pegel in Metern relativ zum Stauziel (0 = Vollstau), wie ihn die Szene verwendet.
CREATE TABLE tal_pegel_messung (
  station_id NUMBER NOT NULL CONSTRAINT tal_pegel_messung_st_fk REFERENCES tal_pegel_station(id) ON DELETE CASCADE,
  tag        DATE   NOT NULL,
  pegel_m    NUMBER(6,2) NOT NULL,
  quelle     VARCHAR2(20) DEFAULT 'BEISPIEL' NOT NULL,
  CONSTRAINT tal_pegel_messung_pk PRIMARY KEY (station_id, tag)
)
/
-- ============================================================ Kamera
CREATE TABLE tal_blickpunkt (
  id        NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT tal_blickpunkt_pk PRIMARY KEY,
  nr        NUMBER(3),
  name      VARCHAR2(60) NOT NULL CONSTRAINT tal_blickpunkt_uk UNIQUE,
  art       VARCHAR2(10) DEFAULT 'STANDARD' NOT NULL CONSTRAINT tal_blickpunkt_art_ck CHECK (art IN ('STANDARD','EIGEN')),
  notiz     VARCHAR2(300),
  ex        NUMBER(12,3) NOT NULL,
  ey        NUMBER(12,3) NOT NULL,
  ez        NUMBER(12,3) NOT NULL,
  tx        NUMBER(12,3) NOT NULL,
  ty        NUMBER(12,3) NOT NULL,
  tz        NUMBER(12,3) NOT NULL,
  angelegt  DATE DEFAULT SYSDATE NOT NULL
)
/
CREATE TABLE tal_fahrt (
  id           NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT tal_fahrt_pk PRIMARY KEY,
  nr           NUMBER(3)    NOT NULL CONSTRAINT tal_fahrt_uk UNIQUE,
  name         VARCHAR2(80) NOT NULL,
  dauer_s      NUMBER(8,1),
  beschreibung VARCHAR2(600)
)
/
CREATE TABLE tal_fahrt_schritt (
  fahrt_id     NUMBER NOT NULL CONSTRAINT tal_fahrt_schritt_f_fk REFERENCES tal_fahrt(id) ON DELETE CASCADE,
  nr           NUMBER(3) NOT NULL,
  dauer_s      NUMBER(8,1),
  kopf         VARCHAR2(120),
  beschreibung VARCHAR2(800),
  uhr_von      NUMBER(4,1),
  uhr_bis      NUMBER(4,1),
  CONSTRAINT tal_fahrt_schritt_pk PRIMARY KEY (fahrt_id, nr)
)
/
CREATE TABLE tal_fahrt_pose (
  fahrt_id   NUMBER NOT NULL,
  schritt_nr NUMBER(3) NOT NULL,
  nr         NUMBER(3) NOT NULL,
  t_s        NUMBER(8,2) NOT NULL,
  ex         NUMBER(12,3) NOT NULL,
  ey         NUMBER(12,3) NOT NULL,
  ez         NUMBER(12,3) NOT NULL,
  tx         NUMBER(12,3) NOT NULL,
  ty         NUMBER(12,3) NOT NULL,
  tz         NUMBER(12,3) NOT NULL,
  CONSTRAINT tal_fahrt_pose_pk PRIMARY KEY (fahrt_id, schritt_nr, nr),
  CONSTRAINT tal_fahrt_pose_s_fk FOREIGN KEY (fahrt_id, schritt_nr) REFERENCES tal_fahrt_schritt(fahrt_id, nr) ON DELETE CASCADE
)
/
-- ============================================================ Voreinstellungen des Bedienfelds
CREATE TABLE tal_voreinstellung (
  id         NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT tal_voreinstellung_pk PRIMARY KEY,
  name       VARCHAR2(60) NOT NULL CONSTRAINT tal_voreinstellung_uk UNIQUE,
  notiz      VARCHAR2(300),
  angelegt   DATE DEFAULT SYSDATE NOT NULL,
  geaendert  DATE DEFAULT SYSDATE NOT NULL
)
/
CREATE TABLE tal_voreinstellung_wert (
  voreinstellung_id NUMBER NOT NULL CONSTRAINT tal_vorwert_v_fk REFERENCES tal_voreinstellung(id) ON DELETE CASCADE,
  schluessel        VARCHAR2(30) NOT NULL,
  wert              VARCHAR2(40) NOT NULL,
  CONSTRAINT tal_vorwert_pk PRIMARY KEY (voreinstellung_id, schluessel)
)
/
-- ============================================================ Bauwerke (Steckbrief)
INSERT INTO tal_bauwerk (kennung, name, art, baujahr, hoehe_m, laenge_m, stauinhalt_mio_m3, bemerkung, geom)
VALUES ('EDERTALSPERRE', 'Edertalsperre (Sperrmauer)', 'SPERRMAUER', 1914, 48, 400, 199.3,
  'Bogengewichtsmauer aus Grauwacke-Bruchstein, Bau 1908 bis 1914, Bogenradius 300 m. Maße nach Literatur; die Linie ist der Kronenbogen des Modells.',
  SDO_GEOMETRY(2002, 8307, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1), SDO_ORDINATE_ARRAY(
    9.061707, 51.183969, 9.062236, 51.183635, 9.062850, 51.183363, 9.063530, 51.183164, 9.064253, 51.183041,
    9.065000, 51.183000, 9.065747, 51.183041, 9.066470, 51.183164, 9.067150, 51.183363, 9.067764, 51.183635,
    9.068293, 51.183969)))
/
INSERT INTO tal_bauwerk (kennung, name, art, baujahr, stauinhalt_mio_m3, bemerkung, geom)
VALUES ('EDERSEE', 'Edersee', 'SEE', 1914, 199.3,
  'Stausee oberhalb der Mauer; die Fläche ist der grobe Umriss des Modells bei Vollstau (Zellen zu 50 m), nicht vermessen.',
  SDO_GEOMETRY(2003, 8307, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1), SDO_ORDINATE_ARRAY(
    9.042787, 51.167897, 9.036338, 51.164732, 9.027740, 51.162471, 9.012692, 51.162019,
    9.004094, 51.163375, 8.991196, 51.167897, 8.986180, 51.168801, 8.972566, 51.168801,
    8.941754, 51.163375, 8.940321, 51.161114, 8.941754, 51.160210, 8.945337, 51.160210,
    8.971133, 51.164732, 8.982597, 51.164732, 8.989046, 51.163827, 8.999078, 51.159758,
    9.009826, 51.157949, 9.011259, 51.157045, 9.027023, 51.157497, 9.043504, 51.161566,
    9.049953, 51.165636, 9.051386, 51.165636, 9.051386, 51.166540, 9.054252, 51.167897,
    9.054252, 51.168801, 9.056401, 51.169706, 9.056401, 51.170610, 9.058551, 51.171514,
    9.062850, 51.176489, 9.064283, 51.176941, 9.067866, 51.181463, 9.067866, 51.183271,
    9.062850, 51.183271, 9.061417, 51.181010, 9.057835, 51.179202, 9.057835, 51.178297,
    9.050669, 51.173323, 9.050669, 51.172419, 9.049236, 51.172419, 9.049236, 51.171514,
    9.042787, 51.167897)))
/
INSERT INTO tal_bauwerk (kennung, name, art, bemerkung, geom)
VALUES ('KRAFTWERK', 'Kraftwerk am Mauerfuß', 'KRAFTWERK',
  'Maschinenhalle mit Uhrenturm am Fuß der Mauer (Modell); Lage umgerechnet.',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(9.066055, 51.183562, NULL), NULL, NULL))
/
INSERT INTO tal_bauwerk (kennung, name, art, bemerkung, geom)
VALUES ('TORTURM', 'Torturm (Ostseite)', 'TURM',
  'Turm am Ostende der Krone (Modell); Lage umgerechnet.',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(9.066300, 51.183127, NULL), NULL, NULL))
/
INSERT INTO tal_bauwerk (kennung, name, art, bemerkung, geom)
VALUES ('ALTE_BRUECKE', 'Alte Brücke (Asel)', 'BRUECKE',
  'Aseler Brücke, rund 7,5 km oberhalb der Mauer am Nordufer des Seearms; taucht bei Niedrigwasser auf (unterhalb etwa 235 m ü. NHN). Lage im Modell nachempfunden.',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(8.962534, 51.165184, NULL), NULL, NULL))
/
INSERT INTO tal_bauwerk (kennung, name, art, bemerkung, geom)
VALUES ('BERICH', 'Berich (Ortsteil, überflutet)', 'ORTSTEIL',
  'Rund 5,9 km oberhalb der Mauer am Nordufer; bei Vollstau überflutet, die Grundmauern erscheinen bei Niedrigwasser. Lage im Modell nachempfunden.',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(8.985463, 51.167942, NULL), NULL, NULL))
/
-- ============================================================ Ereignisse
INSERT INTO tal_ereignis (jahr, titel, beschreibung, bauwerk_id)
VALUES (1908, 'Baubeginn', 'Beginn der Arbeiten an der Sperrmauer im Edertal.', (SELECT id FROM tal_bauwerk WHERE kennung = 'EDERTALSPERRE'))
/
INSERT INTO tal_ereignis (jahr, titel, beschreibung, bauwerk_id)
VALUES (1914, 'Fertigstellung', 'Die Mauer ist fertig, der See füllt sich.', (SELECT id FROM tal_bauwerk WHERE kennung = 'EDERTALSPERRE'))
/
INSERT INTO tal_ereignis (jahr, datum, titel, beschreibung, bauwerk_id)
VALUES (1943, DATE '1943-05-17', 'Bresche', 'In der Nacht zum 17. Mai 1943 reißt ein Luftangriff eine Bresche in die Mauer; die Flutwelle läuft das Edertal hinab.', (SELECT id FROM tal_bauwerk WHERE kennung = 'EDERTALSPERRE'))
/
INSERT INTO tal_ereignis (jahr, titel, beschreibung, bauwerk_id)
VALUES (1943, 'Wiederaufbau', 'Die Bresche wird in den Monaten danach wieder geschlossen.', (SELECT id FROM tal_bauwerk WHERE kennung = 'EDERTALSPERRE'))
/
-- ============================================================ Pegel: Beispielreihen (keine Messwerte)
INSERT INTO tal_pegel_station (kennung, name, bemerkung, geom)
VALUES ('BEISPIEL_MAUER', 'Beispiel: Seepegel an der Mauer', 'Erfundene Beispielreihe für die Szene, keine Messwerte.',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(9.065000, 51.183000, NULL), NULL, NULL))
/
INSERT INTO tal_pegel_station (kennung, name, bemerkung, geom)
VALUES ('BEISPIEL_ASEL', 'Beispiel: Seepegel im Seearm bei Asel', 'Erfundene Beispielreihe für die Szene, keine Messwerte.',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(8.962534, 51.165184, NULL), NULL, NULL))
/
-- Ein Jahr täglich: voll um den 30. April (Tag 120), am tiefsten im Herbst (rund −20 m), dazu kleine Schwankungen.
INSERT INTO tal_pegel_messung (station_id, tag, pegel_m)
SELECT s.id, DATE '2025-01-01' + l.n - 1,
       ROUND(LEAST(0.3, -10 * (1 - COS(2 * ACOS(-1) * (l.n - 120) / 365)) + 0.4 * SIN(l.n / 3.1 + s.id)), 2)
  FROM tal_pegel_station s,
       (SELECT LEVEL AS n FROM dual CONNECT BY LEVEL <= 365) l
/
-- ============================================================ Voreinstellungen (Schlüssel wie im Bedienfeld)
INSERT INTO tal_voreinstellung (name, notiz) VALUES ('Morgendunst am See', 'Spätsommer, Nebel über dem See, Licht in den Schwaden')
/
INSERT INTO tal_voreinstellung_wert (voreinstellung_id, schluessel, wert)
SELECT v.id, k.s, k.w FROM tal_voreinstellung v,
  (SELECT 'hour' s, '6.8' w FROM dual UNION ALL SELECT 'day', '270' FROM dual UNION ALL SELECT 'haze', '0.35' FROM dual
   UNION ALL SELECT 'fog', '1.6' FROM dual UNION ALL SELECT 'lakeMist', '1.6' FROM dual UNION ALL SELECT 'sprayMist', '1.2' FROM dual
   UNION ALL SELECT 'plant', '1.5' FROM dual UNION ALL SELECT 'rays', '1' FROM dual UNION ALL SELECT 'rayGain', '2' FROM dual
   UNION ALL SELECT 'wisps', '1' FROM dual) k
 WHERE v.name = 'Morgendunst am See'
/
INSERT INTO tal_voreinstellung (name, notiz) VALUES ('Sommermittag, Ablässe offen', 'Regenbogen in der Gischt, voller See')
/
INSERT INTO tal_voreinstellung_wert (voreinstellung_id, schluessel, wert)
SELECT v.id, k.s, k.w FROM tal_voreinstellung v,
  (SELECT 'hour' s, '13.25' w FROM dual UNION ALL SELECT 'day', '190' FROM dual UNION ALL SELECT 'level', '0' FROM dual
   UNION ALL SELECT 'open', '1' FROM dual UNION ALL SELECT 'gate1', '1' FROM dual UNION ALL SELECT 'gate2', '1' FROM dual
   UNION ALL SELECT 'gate3', '1' FROM dual UNION ALL SELECT 'gate4', '1' FROM dual UNION ALL SELECT 'gate5', '1' FROM dual
   UNION ALL SELECT 'rainbow', '1' FROM dual UNION ALL SELECT 'haze', '0.12' FROM dual) k
 WHERE v.name = 'Sommermittag, Ablässe offen'
/
INSERT INTO tal_voreinstellung (name, notiz) VALUES ('Hochwasser mit Überlauf', 'See über der Schwelle, Wasser fällt über beide Überläufe')
/
INSERT INTO tal_voreinstellung_wert (voreinstellung_id, schluessel, wert)
SELECT v.id, k.s, k.w FROM tal_voreinstellung v,
  (SELECT 'hour' s, '11' w FROM dual UNION ALL SELECT 'day', '110' FROM dual UNION ALL SELECT 'level', '0.9' FROM dual
   UNION ALL SELECT 'wind', '0.6' FROM dual UNION ALL SELECT 'open', '1' FROM dual) k
 WHERE v.name = 'Hochwasser mit Überlauf'
/
INSERT INTO tal_voreinstellung (name, notiz) VALUES ('Niedrigwasser im Herbst', 'Tiefer See, die Brücke im Seearm taucht auf')
/
INSERT INTO tal_voreinstellung_wert (voreinstellung_id, schluessel, wert)
SELECT v.id, k.s, k.w FROM tal_voreinstellung v,
  (SELECT 'hour' s, '11' w FROM dual UNION ALL SELECT 'day', '300' FROM dual UNION ALL SELECT 'level', '-22' FROM dual
   UNION ALL SELECT 'open', '0.2' FROM dual UNION ALL SELECT 'wind', '0.2' FROM dual) k
 WHERE v.name = 'Niedrigwasser im Herbst'
/
INSERT INTO tal_voreinstellung (name, notiz) VALUES ('Nachtbetrieb', 'Lampen an, Verkehr ruht')
/
INSERT INTO tal_voreinstellung_wert (voreinstellung_id, schluessel, wert)
SELECT v.id, k.s, k.w FROM tal_voreinstellung v,
  (SELECT 'hour' s, '22.5' w FROM dual UNION ALL SELECT 'day', '200' FROM dual UNION ALL SELECT 'lamps', '1' FROM dual
   UNION ALL SELECT 'traffic', '0' FROM dual UNION ALL SELECT 'walkers', '0' FROM dual UNION ALL SELECT 'bloom', '1' FROM dual) k
 WHERE v.name = 'Nachtbetrieb'
/
-- ============================================================ Spatial: Metadaten und Indizes
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('TAL_BAUWERK', 'GEOM', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('Long', -180, 180, 0.05), SDO_DIM_ELEMENT('Lat', -90, 90, 0.05)), 8307)
/
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('TAL_PEGEL_STATION', 'GEOM', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('Long', -180, 180, 0.05), SDO_DIM_ELEMENT('Lat', -90, 90, 0.05)), 8307)
/
COMMIT
/
CREATE INDEX tal_bauwerk_sidx ON tal_bauwerk(geom) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2
/
CREATE INDEX tal_pegel_station_sidx ON tal_pegel_station(geom) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2
/
