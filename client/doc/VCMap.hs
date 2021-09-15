<?xml version='1.0' encoding='ISO-8859-1' ?>
<!DOCTYPE helpset
  PUBLIC "-//Sun Microsystems Inc.//DTD JavaHelp HelpSet Version 2.0//EN"
         "http://java.sun.com/products/javahelp/helpset_2_0.dtd">

<helpset version="2.0">

  <!-- title -->
  <title>VCMap by Bio::Neos - Help</title>

  <!-- maps -->
  <maps>
     <homeID>top</homeID>
     <mapref location="Map.jhm"/>
  </maps>

  <!-- views -->
  <view>
    <name>TOC</name>
    <label>Table Of Contents</label>
    <type>javax.help.TOCView</type>
    <data>VCMapTOC.xml</data>
    <image>tocImage</image>
  </view>

  <!--<view>
    <name>Index</name>
    <label>Index</label>
    <type>javax.help.IndexView</type>
    <data>VCMapIndex.xml</data>
  </view>-->

  <view image="searchImage">
    <name>Search</name>
    <label>Search</label>
    <image>searchImage</image>
    <type>javax.help.SearchView</type>
    <data engine="com.sun.java.help.search.DefaultSearchEngine">
      JavaHelpSearch
    </data>
  </view>

  <presentation default="true" displayviewimages="true">
    <name>main window</name>
    <size width="850" height="600" />
    <location x="400" y="400" />
    <title>VCMap by Bio::Neos - Help Documentation</title>
    <image>toplevelfolder</image>
    <toolbar>
      <helpaction image="backImage">javax.help.BackAction</helpaction>
      <helpaction image="forwardImage">javax.help.ForwardAction</helpaction>
      <helpaction>javax.help.SeparatorAction</helpaction>
      <helpaction image="homeImage">javax.help.HomeAction</helpaction>
      <helpaction>javax.help.SeparatorAction</helpaction>
      <helpaction image="printImage">javax.help.PrintAction</helpaction>
    </toolbar>
  </presentation>
</helpset>
