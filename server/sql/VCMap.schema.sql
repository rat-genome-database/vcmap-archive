DROP TABLE IF EXISTS annotation_avps;
DROP TABLE IF EXISTS annotation_sets;
DROP TABLE IF EXISTS vals;
DROP TABLE IF EXISTS attributes;
DROP TABLE IF EXISTS annotation;
DROP TABLE IF EXISTS links;
CREATE TABLE annotation_sets
(
  id INT NOT NULL AUTO_INCREMENT,
  version_id INT NOT NULL,
  map_id INT NOT NULL,
  type VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  INDEX map_id_idx (map_id)
);
CREATE TABLE annotation
(
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255),
  chromosome_id INT NOT NULL,
  annotation_set_id INT NOT NULL,
  start INT NOT NULL,
  stop INT NOT NULL,
  source_ref_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  INDEX chrid_idx (chromosome_id),
  INDEX aset_idx (annotation_set_id),
  INDEX name_idx (name),
  INDEX srcref_idx (source_ref_id)
);
CREATE TABLE attributes
(
  id INT NOT NULL AUTO_INCREMENT,
  type VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (type)
);
CREATE TABLE vals
(
  id BIGINT NOT NULL AUTO_INCREMENT,
  value TEXT NOT NULL,
  sha1 CHAR(40) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (sha1)
);
CREATE TABLE annotation_avps
(
  annotation_id BIGINT NOT NULL,
  attribute_id INT NOT NULL,
  value_id BIGINT NOT NULL,
  INDEX val_idx (value_id),
  INDEX attr_idx (attribute_id),
  UNIQUE (annotation_id, attribute_id, value_id)
);
CREATE TABLE links
(
  id BIGINT NOT NULL,
  annotation_id BIGINT NOT NULL,
  source_id INT NULL,
  INDEX link_id_idx (id),
  INDEX annotation_id_idx (annotation_id),
  UNIQUE(id, annotation_id)
);

DROP TABLE IF EXISTS maps;
DROP TABLE IF EXISTS chromosomes;
DROP TABLE IF EXISTS synteny;
CREATE TABLE maps
(
  id INT NOT NULL AUTO_INCREMENT,
  version_id INT NOT NULL, 
  type ENUM('Genomic', 'Genetic', 'RH', 'Linkage', 'Cytoband') NOT NULL,
  species VARCHAR(255) NOT NULL,
  units ENUM('bp', 'cM', 'cR'),
  scale SMALLINT NOT NULL DEFAULT 1,
  disabled INT(1) NOT NULL DEFAULT 0,
  default_annotation INT(11) NULL,
  taxID INT NOT NULL,
  PRIMARY KEY (id)
);
CREATE TABLE chromosomes
(
  id INT NOT NULL AUTO_INCREMENT,
  map_id INT NOT NULL,
  name VARCHAR(16) NOT NULL,
  length INT NOT NULL,
  PRIMARY KEY (id),
  INDEX mapid_idx (map_id)
);
CREATE TABLE synteny
(
  id INT NOT NULL AUTO_INCREMENT,
  left_id INT NOT NULL,
  right_id INT NOT NULL,
  left_start_id BIGINT NOT NULL,
  right_start_id BIGINT NOT NULL,
  left_stop_id BIGINT NOT NULL,
  right_stop_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS sources;
DROP TABLE IF EXISTS source_urls;
CREATE TABLE sources
(
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
);
CREATE TABLE source_urls
(
  source_id INT NOT NULL,
  target ENUM('annotation', 'map', 'chromosome', 'homologene', 'home') NOT NULL,
  type varchar(255),
  key_value VARCHAR(255),
  url TEXT NOT NULL,
  UNIQUE (source_id, target, type)
);

DROP TABLE IF EXISTS ontology_children;
DROP TABLE IF EXISTS ontology_node;
DROP TABLE IF EXISTS ontology_tree;
CREATE TABLE ontology_children
(
  node_id INT NOT NULL,
  child_id INT NOT NULL,
  UNIQUE (node_id, child_id)
);
CREATE TABLE ontology_node
(
  id INT NOT NULL AUTO_INCREMENT,
  tree_id INT NOT NULL,
  parent_node_id INT NULL,
  category VARCHAR(255) NOT NULL,
  description TEXT,
  INDEX cat_idx (category),
  PRIMARY KEY (id)
);
CREATE TABLE ontology_tree
(
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(255) NOT NULL,
  version VARCHAR(64),
  loaded DATE NOT NULL,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS versions;
CREATE TABLE versions
(
  id INT NOT NULL AUTO_INCREMENT,
  loaded DATE NOT NULL,
  source_id INT NOT NULL,
  generic VARCHAR(255) NULL,
  build INT NULL,
  version INT NULL,
  md5 CHAR(32) NULL,
  release_date DATE NULL,
  human_name VARCHAR(255) NULL,
  PRIMARY KEY (id)
);

/* Hardcoded source data: */
INSERT INTO sources (id, name) VALUES 
  (1, 'NCBI Genomes'), (2, 'RGD'), (3, 'ISU'), (4, 'Ensembl'), (5, 'UMD'), (6, 'NCBI Gene'), (7, 'NCBI UniSTS'), (8, 'NCBI Homologene');

/* URL data: */
  /* NCBI urls */
INSERT INTO source_urls (source_id, target, type, key_value, url) VALUES
  (1, 'home', 'genome', null, 'http://www.ncbi.nlm.nih.gov/sites/entrez?db=genome'),
  (1, 'home', 'map', null, 'http://www.ncbi.nlm.nih.gov/mapview/'),
  (1, 'map', null, null, 'http://www.ncbi.nlm.nih.gov/projects/mapview/map_search.cgi?taxid={taxID}'),
  (1, 'chromosome', null, null, 'http://www.ncbi.nlm.nih.gov/projects/mapview/maps.cgi?taxid={taxID}&chr={chrID}'),
  (1, 'chromosome', 'interval', null, 'http://www.ncbi.nlm.nih.gov/projects/mapview/maps.cgi?taxid={taxID}&chr={chrID}&BEG={start}&END={stop}'),

  /* NCBI UniSTS */
  (7, 'home', 'STS', null, 'http://www.ncbi.nlm.nih.gov/sites/entrez?db=unists'),
  (7, 'annotation', 'STS', 'stsID', 'http://www.ncbi.nlm.nih.gov/genome/sts/sts.cgi?uid={stsID}'),

  /* NCBI Gene */
  (6, 'home', 'GENE', null, 'http://www.ncbi.nlm.nih.gov/sites/entrez?db=gene'),
  (6, 'annotation', 'GENE', 'geneID', 'http://www.ncbi.nlm.nih.gov/sites/entrez?cmd=search&db=gene&term={geneID}'),
  (6, 'annotation', 'PSEUDO', 'geneID', 'http://www.ncbi.nlm.nih.gov/sites/entrez?cmd=search&db=gene&term={geneID}'),

  /* Homologene data */
  (8, 'home', 'homologene', null, 'http://www.ncbi.nlm.nih.gov/sites/entrez?db=homologene'),
  (8, 'annotation', 'homologene', 'homologeneID', 'http://www.ncbi.nlm.nih.gov/sites/entrez?db=homologene&term={homologeneID}'),

  /* RGD urls */
  (2, 'home', 'QTL', null, 'http://rgd.mcw.edu/rgdweb/search/qtls.html'),
  (2, 'annotation', 'QTL', 'qtlID', 'http://rgd.mcw.edu/objectSearch/qtlReport.jsp?rgd_id={qtlID}'),
  (2, 'annotation', 'GENE', 'geneID', 'http://rgd.mcw.edu/tools/genes/genes_view.cgi?id={geneID}'),

  /* ISU urls */
  (3, 'home', 'QTL', null, 'http://www.animalgenome.org/QTLdb/'),
  (3, 'annotation', 'QTL', 'qtlID', 'http://www.animalgenome.org/cgi-bin/QTLdb/{species}/qdetails?QTL_ID={qtlID}'),

  /* Ensembl urls */
  (4, 'annotation', 'GENE', 'geneID', 'http://uswest.ensembl.org/{species}/Gene/Summary?g={geneID}'),
  (4, 'annotation', 'PSEUDO', 'geneID', 'http://uswest.ensembl.org/{species}/Gene/Summary?g={geneID}'),
  
  /* UMD urls */
  (5 ,'annotation', 'GENE', 'geneID', 'http://www.ncbi.nlm.nih.gov/sites/entrez?cmd=search&db=gene&term={geneID}');
