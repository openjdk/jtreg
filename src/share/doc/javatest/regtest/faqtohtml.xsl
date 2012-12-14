<?xml version="1.0" encoding="UTF-8" ?>

<!--
    Document   : faqtohtml.xsl
    Created on : September 8, 2006, 10:37 AM
    Author     : jjg
    Description:
        Purpose of transformation follows.
-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="html"/>
    <xsl:param name="context" select="''"/>
    <xsl:template match="/faq">
        <html>
            <head>
                <title>The "Regression Test Harness for the OpenJDK platform: jtreg" FAQ</title>
                <style type="text/css">
                    hr { margin-top:20px }
                    ol.li { margin-top:20px }
                </style>
            </head>
            <body>

                <h1>The "Regression Test Harness for the OpenJDK platform: jtreg" FAQ</h1>

                <p>This FAQ is a growing list of questions asked by developers writing tests
                which will be run using the regression test harness for the OpenJDK platform, jtreg.
                It is a supplement to the test-tag language <a href="tag-spec.txt">specification</a>
                and is intended to illuminate implications of the spec and to answer questions
                about this implementation of the spec.</p>

                <hr/>
                <h2>Index</h2>
                
                <ol>
                    <xsl:for-each select="//group">
                        <xsl:variable name="groupIndex" select="position()"/>
                        <li>
                            <a href="#group{$groupIndex}">
                                <xsl:value-of select="@title"/>
                            </a>
                            <ol>
                                <xsl:for-each select="entry[not(answer/@context) or answer/@context = $context]">
                                    <li>
                                        <a href="#question{$groupIndex}.{position()}">
                                            <xsl:copy-of select="question/node()"/>
                                        </a>
                                    </li>
                                </xsl:for-each>
                            </ol>
                        </li>
                    </xsl:for-each>
                </ol>
                
                
                <xsl:for-each select="//group">
                    <p/>
                    <hr/>
                    <xsl:variable name="groupIndex" select="position()"/>
                    <h2>
                        <a name="group{$groupIndex}">
                            <xsl:value-of select="$groupIndex"/>. <xsl:value-of select="@title"/>
                        </a>
                    </h2>
                    <xsl:for-each select="entry[not(answer/@context) or answer/@context = $context]">
                        <h3>
                            <a name="question{$groupIndex}.{position()}"><xsl:value-of select="$groupIndex"/>.<xsl:value-of select="position()"/>.
                                <xsl:copy-of select="question/node()"/>
                            </a>
                        </h3>
                        
                        <xsl:copy-of select="answer[not(@context) or @context = $context]/node()"/>
                    </xsl:for-each>
                </xsl:for-each>
                

                <hr/>
                
                <xsl:choose>
                    <xsl:when test="$context='sun'">
                        <dl>
                            <dt><b><i>The information I'm looking for isn't in the FAQ but should be.  I
                            still have questions.</i></b></dt>

                            <dd><p>Send additional questions to 
                                <a href="mailto:jtreg-comments@sfbay.sun.com">jtreg-comments@sfbay.sun.com</a>.</p>

                            </dd>
                        </dl>
                    </xsl:when>
                    <xsl:when test="$context='openjdk'">
                        <dl>
                            <dt><b><i>The information I'm looking for isn't in the FAQ but should be.  I
                            still have questions.</i></b></dt>

                            <dd><p>Send additional questions to 
                                <a href="mailto:jtreg-discuss@openjdk.java.net">jtreg-discuss@openjdk.java.net</a>.</p>

                            </dd>
                        </dl>
                    </xsl:when>
                </xsl:choose>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
