package crosscut

import typings.node.anon.ObjectEncodingOptionsflagEncoding
import typings.node.fsMod
import typings.obsidian.mod
import typings.obsidian.mod.FileSystemAdapter
import utils.Utils

import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as l

/**
 * # object CrossCuttingConcerns
 * Setup all the cross cutting concerns for the solution threads. These solution threads are marked by using markers
 * that is picked up from the document md files and placed in the solution folder.
 *
 */
object CrossCuttingConcerns:

  private type DOCNAME   = String
  private type DOCSTRING = String
  private type MARKER    = String
  private type SOLNAME   = String
  private type PATHNAME  = String

  def apply(app : mod.App, storyFolder : String,  solutionFolder : String, docFolder: String, markerMapping : String) : Unit =
    //
    //bus if a mapping string has been defined then get the mappings : format is 'marker'='mapping-value'
    //
    val vaultPath = app.vault.adapter.asInstanceOf[FileSystemAdapter].getBasePath()

    val markerMappings : Map[String, String] = if markerMapping.nonEmpty then
      markerMapping
        .split("\n")
        .map( value =>
          if value.contains("=") then
            val lst = value.split("=")
            ( lst(0), lst(1) )
          else
            ( "", "" )
        )
        .toMap
    else
      Map[String, String]()
    //
    // some containers to use later on
    //
    val markerToDocumentMap     = mutable.HashMap[MARKER, DOCNAME]()
    val documentToMarkerMap     = mutable.HashMap[DOCNAME, List[MARKER]]()
    val solutionToMarkerMap     = mutable.HashMap[SOLNAME, List[MARKER]]()
    val allSolutionFiles        = mutable.HashSet[MARKER]()
    //
    // get all the doc files to scan
    //
    val documentPath = s"$vaultPath${Utils.separator}$docFolder"
    val documentFiles = Utils.walk(documentPath).filter(name => name.endsWith(".md")).toList
    //
    //bus remove all the solution files and the then empty solution folder
    //
    val solutionPath = s"$vaultPath${Utils.separator}$solutionFolder"
    val solutionFiles = Utils.walk(solutionPath).filter(name => name.endsWith(".md")).toList
    solutionFiles.foreach(file => fsMod.unlinkSync(file))
    fsMod.rmSync(solutionPath, l(recursive =  true, force = true).asInstanceOf[fsMod.RmOptions])
    //
    // pick up all markers in the doc string doc file by doc file and aggregate the markers
    // before processing them
    //
    val markerList = mutable.ListBuffer[String]()
    documentFiles.foreach(docFile =>
      val str = fsMod.readFileSync(docFile, l(encoding = "utf8", flag = "r")
        .asInstanceOf[ObjectEncodingOptionsflagEncoding])
        .asInstanceOf[String]

      val markersMatch = Utils.markerRegExp.findAllMatchIn(str)
      val markersPerDocument = markersMatch.map(marker => str.substring(marker.start, marker.end).trim).toList

      markerList ++= markersPerDocument

      val documentName = docFile.split(Utils.separatorRegEx).last

      markersPerDocument.foreach(marker =>
        markerToDocumentMap += (marker -> documentName)
      )

      documentToMarkerMap += (documentName -> markersPerDocument)

    )
    //
    // collect all markers in one list
    // sort them then
    // group by path/name.md excluding the seq number
    //
    val allMarkers = documentToMarkerMap
      .values
      .toList
      .flatten
      .sortWith( (s1, s2) =>
        s1 < s2
      )
      .groupBy(by => solutionDocNameFromMarker(solutionFolder, by))
    //
    // write out
    //
    allMarkers.foreach( ( solName, markers ) =>
      //
      // build link to story
      //
      val mdString = StringBuilder(s"""![[$storyFolder${Utils.separator}${getStoryFileName(solName.dropRight(3), markerMappings)}#^summary]]\n""")
      markers.foreach(marker =>
        //
        // build links to document thread
        //
        mdString ++= s"""![[${markerToDocumentMap(marker)}#${marker}]]\n"""
      )
      //
      // remove solution file it exists and recreate with new values.
      //
      val marker = markers.head.drop(1).split("-").dropRight(1).mkString("-")
      val solNameWithPath = getSolutionFileName(marker, s"$vaultPath${Utils.separator}$solName", markerMappings)
      //
      // create the folder path if required
      //
      val pathToCreate = solNameWithPath.split(Utils.separatorRegEx).dropRight(1).mkString(Utils.separator)
      fsMod.mkdirSync(pathToCreate, l(recursive =  true).asInstanceOf[fsMod.MakeDirectoryOptions])
      //
      // write of the solution text
      //
      fsMod.writeFile(solNameWithPath, mdString.toString(), err => ())
    )

  private def getSolutionFileName(marker : String,  solName : String, mapping : Map[String, String]) : String =
    // get solution name strip off the .md
    if mapping.contains(marker) then
      solName.replace(marker, mapping(marker))
    else
      solName

  /**
   * given the solution name return the story name, if the story name is in mapping then use that rather
   *
   * @param solName to use for the story name
   * @return the story name
   */
  private def getStoryFileName(solName : SOLNAME, mapping : Map[String, String]) : String =
    val nameList = solName.split(Utils.separatorRegEx).drop(1)
    val storyName = nameList.last
    if mapping.contains(storyName) then
      s"${nameList.dropRight(1).mkString(Utils.separator)}${mapping(storyName)}"
    else
      nameList.mkString(Utils.separator)

  /**
   * Take a marker string and convert into file path / file name.md. If the marker excluding the -[0-9]+
   * is in mapping then use that mapping value
   * @param marker in document string
   * @return
   */
  private def solutionDocNameFromMarker(solFolder : String, marker : String): SOLNAME =
    val markerList = marker.drop(1).split("-")
    val docName = markerList.dropRight(1).mkString(Utils.separator)
    val solutionName = s"${docName}.md"
    s"""$solFolder${Utils.separator}$solutionName"""

  /**
   * Take a marker string and convert into file path
   *
   * @param marker
   * @return
   */
  private def solutionPathFromMarker(solFolder : String, marker: String): PATHNAME =
    val markerStr = marker.drop(1)
    val markerList = markerStr.split("-").dropRight(2)
    val pathName = s""""${markerList.dropRight(1).mkString(Utils.separator)}"""

    s"""$solFolder${Utils.separator}$pathName"""
