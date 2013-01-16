
CREATE TABLE city (
    id serial NOT NULL,
    name text NOT NULL,
    countrycode character(3) NOT NULL,
    district text,
    population integer
);

CREATE TABLE country (
    code character(3) NOT NULL,
    name text NOT NULL,
    continent text NOT NULL,
    region text NOT NULL,
    surfacearea real NOT NULL,
    indepyear smallint,
    population integer NOT NULL,
    lifeexpectancy real,
    gnp numeric(10,2),
    gnpold numeric(10,2),
    localname text NOT NULL,
    governmentform text NOT NULL,
    headofstate text,
    capital integer,
    code2 character(2) NOT NULL,
    CONSTRAINT country_continent_check CHECK ((((((((continent = 'Asia'::text) OR (continent = 'Europe'::text)) OR (continent = 'North America'::text)) OR (continent = 'Africa'::text)) OR (continent = 'Oceania'::text)) OR (continent = 'Antarctica'::text)) OR (continent = 'South America'::text)))
);

CREATE TABLE countrylanguage (
    countrycode character(3) NOT NULL,
    "language" text NOT NULL,
    isofficial boolean NOT NULL,
    percentage real NOT NULL
);

INSERT INTO country VALUES ('FRA', 'France', 'Europe', 'Western Europe', 551500, 843, 59225700, 78.800003, 1424285.00, 1392448.00, 'France', 'Republic', 'Francois Hollande', 2974, 'FR');
INSERT INTO country VALUES ('ITA', 'Italy', 'Europe', 'Southern Europe', 301316, 1861, 57680000, 79, 1161755.00, 1145372.00, 'Italia', 'Republic', 'Carlo Azeglio Ciampi', 1464, 'IT');

INSERT INTO countrylanguage VALUES ('FRA', 'French', 't', 93.599998);

INSERT INTO city VALUES (1464, 'Roma', 'ITA', 'Latium', 2643581);
INSERT INTO city VALUES (2974, 'Paris', 'FRA', 'Ile-de-France', 2125246);
