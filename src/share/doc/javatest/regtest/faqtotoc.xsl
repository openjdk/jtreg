<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" indent="yes" 
        doctype-public="-//Sun Microsystems Inc.//DTD JavaHelp Map Version 1.0//EN"
        doctype-system="http://java.sun.com/products/javahelp/toc_1_0.dtd">
    </xsl:output>
    <xsl:param name="context" select="''"/>
    <xsl:template match="/faq">
        <toc version="1.0">
            <tocitem image="openbook" target="home" text="Introduction"/>
            <tocitem image="openbook" target="spec" text="Tag Language Specification"/>
            <tocitem image="openbook" target="faq" text="Frequently Asked Questions">
                <xsl:for-each select="group">
                    <tocitem image="chapter" target="faq_{position()}" text="{position()} {@title}" />
                </xsl:for-each>
            </tocitem>
            <tocitem image="openbook" target="usage" text="Command Line Usage"/>
            <tocitem image="openbook" target="ant" text="Using jtreg with Ant"/>
        </toc>
    </xsl:template>

</xsl:stylesheet>
