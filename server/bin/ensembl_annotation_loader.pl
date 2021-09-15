#! /usr/bin/perl -w

#####################################################################################
## This script will load various annotation types from the Ensembl databases
## @author Michael F Smith, MFS
## @date 06.01.10
## @date 11.29.11
##
## Some of the steps taken throughout this script to load data might be seen as 
## somewhat of a hack and should be improved upon as new methods become available. 
## These sections will be noted in the code below. This also has a hardcoded location
## where the resulting GFF3 annotation files are stored.
##
## To load annotation:
##   1) Read config options from config file
##
##   2) Find our source_id that corresponds to Ensembl
##
##   5) For the specified species, gather all chromosomes from an existing map
##      + This is somewhat of a hack because of what Ensembl considers a chr
##
##   6) Gather specified annotation for each chromosome
##  
##   7) Create a GFF3 compatible line from the annotation with available attributes
##
##   8) Write to the appropriate file or print to STDOUT
#####################################################################################

use lib '/usr/local/ensembl.API/ensembl/modules/' ;
use lib '/usr/local/ensembl.API/ensembl-variation/modules/' ;
use DBI ;
use JSON ;
use Getopt::Long ;
use Bio::EnsEMBL::Registry ;
use Bio::EnsEMBL::ApiVersion ;
use Time::HiRes qw(gettimeofday) ;
use Log::Log4perl qw(get_logger :levels) ;

# Some constants
my $LOGGER ;
my $RELEASE = 0 ;
my $FEATURE = 4 ;
my $BUGFIX = 0 ;
my $ENSEMBL_API_VER = software_version() ;
my @SRC_FIELDS = qw(name) ;
my $SOURCE_SQL = "SELECT id FROM sources WHERE name LIKE '%Ensembl'" ;
my $CHR_NAME_SQL = "SELECT DISTINCT name FROM chromosomes c, maps m WHERE c.map_id = m.id AND m.species = ? AND m.type = ?" ;
my $INSERT_SRC_SQL = "INSERT INTO sources (" . join(", ", @SRC_FIELDS) . ") VALUES (" . calcQMarks(@SRC_FIELDS) . ")" ;
my %SUPP_GENE_TYPES = ( 
  "pseudogene" => "PSEUDO", 
  "protein_coding" => "GENE" 
) ;
my %CHARS_TO_ESCAPE = (
  ";" => "%3B",
  "=" => "%3D",
  "%" => "%25",
  "&" => "%26",
  "," => "%2C",
  "\t" => "%09",
  "\n" => "%0A",
  "\r" => "%0D",
  "\"" => "\\\"",
  "'" => "\\\'"
) ;

# Local vars
my %annoTypes ;
my @annotTypeTemp ;
my $confObj ;
my $confFile ;
my $confString ;
my $sliceAdapter ;
my $output;
my $registry = "Bio::EnsEMBL::Registry" ;

# 1) Parse our command line options
GetOptions(
  "config|conf|c=s" => \$confString,
  "config_file=s", \$confFile,
  "output|o=s", \$output,
  "annotation|annot|a=s" => \@annoTypesTemp
) ;

# DOC: Usage method needed!

# Setup our logging
Log::Log4perl::init("config/ensembl_logger.conf") ;
$LOGGER = get_logger("ensembl") ;
$LOGGER->info("Ensembl loading script [v.$RELEASE.$FEATURE.$BUGFIX] started") ;

# TODO: This should likely be in one method to check for options, defaults, etc
# Basic req checks first, We need a config file, error if not provided
if(!defined($confString) && (!defined($confFile) || !(-e $confFile))) 
{
  $LOGGER->fatal("Either a JSON formatted configuration parameter is required or a JSON formatted file!") ;
  exit ;
}

if(!defined($output))
{
  $LOGGER->fatal("The output dir must be specified!") ;
  exit ;
}

if(!@annoTypesTemp)
{
  push(@annoTypesTemp, "all") ;
}

# Translate our array to a hash for quick lookup
foreach (@annoTypesTemp)
{
  $annoTypes{$_} = 1 ;
}

$LOGGER->debug("Command line options:") ;
if(defined($confString))
{
  $LOGGER->debug("    [Config String: $confString]") ;
}
else
{
  $LOGGER->debug("    [Config File: $confFile]") ;
}
$LOGGER->debug("    [Annotation Types: " . join(", ", @annoTypesTemp) . "]") ;

# Read our conf file into memory
if(defined($confString))
{
  $confObj = decode_json($confString) ;
}
else
{
  # If we arent the JSON string, we have to be given the JSON conf file
  open(JSON, $confFile) ;
  my $json = join(" ", <JSON>) ;
  $confObj = decode_json($json) ;
  close(JSON) ;
}

# Basic check, if no species exist, exit
if(($#{$confObj->{species}} + 1) < 1)
{
  $LOGGER->warn("No species to load from config file, finishing...") ;
  exit ;
}

# Create our connection to Ensembl
$registry->load_registry_from_db(
  -host => "ensembldb.ensembl.org",
  -user => "anonymous"
);

# Everything is setup, get into some data loading, process every species specified
foreach my $speciesObj (@{$confObj->{species}})
{
  my $dbRetVal = 1 ;
  my $mapId = -1 ;
  my %ensemblEntrezMap ;
  my $taxId = $speciesObj->{taxID} ;
  my $species = $speciesObj->{name} ; 
  $sliceAdapter = $registry->get_adaptor($species, "Core", "Slice") ;

  $LOGGER->debug("Processing $species...") ;
  
  # Now read each annotation object
  foreach my $annotObj (@{$speciesObj->{annotations}})
  {
    my $assembly = $annotObj->{assembly_version} ;
    my $assembly_type = $annotObj->{assembly_type};
    my $annotType = $annotObj->{type};
  
    $LOGGER->debug("Processing annotations of type $annotType...");
    # First we need to do some checks to make sure we have all our info for this species
    if(!defined($annotObj->{db_name}))
    {
      $LOGGER->error("$species:$annotType does not have a defined database (db_name) to connect to, skipping...") ;
      next ;
    }
    
    # Check that our supplied DB name matches what we connected to
    my $trimmedDb = $annotObj->{db_name} ;
    $trimmedDb =~ s/^\s+// ;
    $trimmedDb =~ s/\s+$// ;
    
    if($sliceAdapter->db()->dbc()->dbname() ne $trimmedDb)
    {
      my $err = "Database names do not match for $species (db in config file: $trimmedDb, " ;
      $err .= "latest Ensembl db: " . $sliceAdapter->db()->dbc->dbname() . "), skipping. Please update your config file." ;
      $LOGGER->error($err) ;
      next ;
    }
    
    # Lastly, we need to make sure we have an assembly version 
    if(!defined($assembly))
    {
      $LOGGER->error("$species:$annotType does not have a defined assembly version (assembly_version), skipping...") ;
      next ; 
    }
    
    # Make sure we create/open our GFF3 files
    system "mkdir -p $output/$species/$assembly_type";
    my $geneGff3File = "$output/$species/$assembly_type/data-$assembly:$ENSEMBL_API_VER.gff3" ;
    $LOGGER->debug("   Opening GFF3 files for writing...") ;
    my $geneFileOpen = open(GENE_FILE, ">$geneGff3File") ;
    if(!defined($geneFileOpen) || $geneFileOpen <= 0)
    {
      $LOGGER->fatal("An error occurred while trying to open the Gene GFF3 File [$geneGff3File]: " . $!) ;
      exit ;
    }
    
    print GENE_FILE "##gff-version 3\n" ;
    print GENE_FILE "##assembly_version $assembly\n";
    
    # 5) For the specified species, gather all chromosomes from an existing map
    foreach my $chrom (@{ $sliceAdapter->fetch_all('chromosome') })
    {
      my $chr = $chrom->seq_region_name();
      # Only use chromosomes without "_" (most of those are random or unplaced)
      if(($chr =~ m/([^_\s]+)/) && (my $ensChr = $sliceAdapter->fetch_by_region("chromosome", $1)))
      {
        if($annoTypes{'all'} || $annoTypes{'genes'})
        {
          # 6) Gather annotation from Ensembl
          writeEnsGenesForChr($species, $taxId, $mapId, $ensChr, $1) ;
        }
      }
      else
      {
        # Should log
        $LOGGER->warn("    $chr could not be found, continuing...") ;
      }
    }
  }
}

$LOGGER->info("Finished loading Ensembl data") ;

# Cleanup
close(GENE_FILE) ;

sub calcQMarks
{
  return join(", ", (("?") x ($#_ + 1))) ;
}

sub writeEnsGenesForChr
{
  my $species = shift ;
  my $taxId = shift ;
  my $mapId = shift ;
  my $ensChr = shift ;
  my $chr = shift ;
  my $noHgId = 0 ;
  my $notSuppCount = 0 ;
  my $dbRetVal = 1 ;
  my $genes = $ensChr->get_all_Genes() ;
  my %geneAnnots ;
  
  $LOGGER->debug("   Processing $chr......" . scalar(@{$genes}) . " genes") ;

  # Optimization: First, build a hash for our gene objects (Ensembl object ids) for this chromosome
  while(my $gene = shift(@{$genes}))
  {
    if(!defined($SUPP_GENE_TYPES{$gene->biotype()}))
    {
      # Gene type not currently supported, skip this annotation
      $notSuppCount++ ;
      next ;
    }
    
    $geneAnnots{$gene->dbID()} = {} ;
    $geneAnnots{$gene->dbID()}{gene} = $gene ;  
    $geneAnnots{$gene->dbID()}{entrezIds} = () ;
  }
  
  # Now, make a query for our EntrezGene IDs for our present Gene objects, assuming we have some
  my @geneKeys = keys(%geneAnnots) ;
  if(($#geneKeys + 1) > 0)
  {
    my $ENSEMBL_ENTREZ_XREF_SQL = "SELECT DISTINCT oxr.ensembl_id, xref.dbprimary_acc "
      . "FROM (xref xref, external_db exDB, object_xref oxr) "
      . "LEFT JOIN external_synonym es on es.xref_id = xref.xref_id "
      . "LEFT JOIN identity_xref idt on idt.object_xref_id = oxr.object_xref_id "
      . "LEFT JOIN ontology_xref gx on gx.object_xref_id = oxr.object_xref_id "
      . "WHERE xref.xref_id = oxr.xref_id "
      . "AND xref.external_db_id = exDB.external_db_id "
      . "AND oxr.ensembl_id IN (" . calcQMarks(@geneKeys) . ") "
      . "AND exDB.db_name = 'EntrezGene'" ;
  
    $LOGGER->debug("      Mapping internal Ensembl IDs to EntrezGene IDs") ;  
    my $ensSth = $sliceAdapter->prepare($ENSEMBL_ENTREZ_XREF_SQL) ;
    $ensSth->execute(@geneKeys) ;
  
    while((my $ensId, my $acc) = $ensSth->fetchrow_array())
    {
      push(@{$geneAnnots{$ensId}{entrezIds}}, $acc) ;
    }
    $ensSth->finish() ;
  }

  while(my $geneId = each(%geneAnnots))
  {
    my $gene = $geneAnnots{$geneId}{gene} ;
    my $geneGffLine = "" ;
    
    # 7) Create a GFF3 compatible line for the piece of annotation
    $geneGffLine .= "$chr\tEnsembl\t" . $SUPP_GENE_TYPES{$gene->biotype()} . "\t" ;
    $geneGffLine .= $gene->start() . "\t" . $gene->end() . "\t.\t" . (($gene->strand() == -1) ? "-" : "+") . "\t.\t" ;
    
    # Begin attributes output with "Name"
    if(defined($gene->external_name()) && $gene->external_name() ne "")
    {
      $geneGffLine .= "Name=" . $gene->external_name() . "; " ;
    }
    else
    {
      $geneGffLine .= "Name=" . $gene->stable_id() . "; " ;
    }
    $geneGffLine .= "source_id=" . $gene->stable_id() . "; status=" . $gene->status() . "; " ;
  
    if(defined($gene->description()) && $gene->description ne "")
    {
      my $escapedRegex = join("|", keys(%CHARS_TO_ESCAPE)) ;
      my $escapedDesc = $gene->description() ;
      
      # Replace our illegal GFF3 characters
      $escapedDesc =~ s/($escapedRegex)/$CHARS_TO_ESCAPE{$1}/g ; 
      $geneGffLine .= "description=" . $escapedDesc . "; " ;
    }
  
    if(defined(@{$geneAnnots{$geneId}{entrezIds}}) && ($#{$geneAnnots{$geneId}{entrezIds}} + 1) > 0)
    #if(($#entrezIds + 1) > 0)
    {
      #print "size = " . scalar(@entrezIds) . ", first = $entrezIds[0]\n" ;
      $geneGffLine .= "EntrezGeneId=" . join("%2C", @{$geneAnnots{$geneId}{entrezIds}}) . "; " ;
    }
    else
    {
      $noHgId++ ;
    }
    
    # 8) Write the line to the correct file or print it to STDOUT
    if($SUPP_GENE_TYPES{$gene->biotype()} =~ /PSEUDO/)
    {
      print GENE_FILE $geneGffLine . "\n" ;
    }
    elsif($SUPP_GENE_TYPES{$gene->biotype()} =~ /GENE/)
    {
      print GENE_FILE $geneGffLine . "\n" ;
    }
    else
    {
      # Right now we only write out GENE or PSEUDO to file, just print if we arent that type
      # NOTE: At this point this is kind of silly because our only two supported types are 
      #     : gene or pseudo. But, futureproof...
      print $geneGffLine . "\n" ;
    }
  }
  
  $LOGGER->info("       $noHgId genes did not have a EntrezGeneId in Ensembl") ;
  $LOGGER->info("       $notSuppCount genes of unsupported types skipped for $species on $chr") ;
}
