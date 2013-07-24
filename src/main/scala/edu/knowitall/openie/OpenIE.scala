package edu.knowitall.openie

import edu.knowitall.srlie.SrlExtraction
import edu.knowitall.tool.tokenize.ClearTokenizer
import edu.knowitall.tool.chunk.OpenNlpChunker
import edu.knowitall.tool.postag.OpenNlpPostagger
import edu.knowitall.tool.parse.ClearParser
import edu.knowitall.tool.postag.ClearPostagger
import edu.knowitall.chunkedextractor.Relnoun
import edu.knowitall.srlie.SrlExtractor
import edu.knowitall.tool.stem.MorphaStemmer
import edu.knowitall.srlie.SrlExtraction.Argument
import edu.knowitall.srlie.SrlExtraction.Relation
import edu.knowitall.chunkedextractor.BinaryExtractionInstance
import edu.knowitall.srlie.SrlExtractionInstance
import edu.knowitall.tool.stem.Lemmatized
import edu.knowitall.tool.chunk.ChunkedToken
import edu.knowitall.tool.parse.graph.DependencyNode
import edu.knowitall.tool.srl.Roles
import edu.knowitall.collection.immutable.Interval
import edu.knowitall.tool.srl.Srl
import edu.knowitall.tool.parse.DependencyParser
import edu.knowitall.tool.srl.ClearSrl
import edu.knowitall.srlie.SrlExtraction

class OpenIE(parser: DependencyParser = new ClearParser(), srl: Srl = new ClearSrl(), triples: Boolean = false) {
  // sentence pre-processors
  val tokenizer = new ClearTokenizer()
  val postagger = new OpenNlpPostagger(tokenizer)
  val chunker = new OpenNlpChunker(postagger)

  // subextractors
  val relnoun = new Relnoun
  val srlie = new SrlExtractor(srl)

  def apply(sentence: String): Seq[Extraction] = extract(sentence)
  def extract(sentence: String): Seq[Extraction] = {
    // pre-process the sentence
    val chunked = chunker(sentence) map MorphaStemmer.lemmatizePostaggedToken
    val parsed = parser(sentence)

    // run extractors
    val srlExtrs = srlie(parsed)
    val relnounExtrs = relnoun(chunked)

    def convertSrl(inst: SrlExtractionInstance): Extraction = {
      def offsets(part: SrlExtraction.MultiPart) = {
        var intervals = part.intervals
        var tokens = part.tokens
        var offsets = List.empty[Interval]
        while (!intervals.isEmpty) {
          val sectionTokens = tokens.take(intervals.head.size)
          tokens = tokens.drop(intervals.head.size)
          intervals = intervals.drop(1)

          offsets ::= Interval.open(sectionTokens.head.offsets.start, sectionTokens.last.offsets.end)
        }

        offsets.reverse
      }
      new Extraction(
        rel = new Part(inst.extr.rel.text, offsets(inst.extr.rel)),
        // can't use offsets field due to a bug in 1.0.0-RC2
        arg1 = new Part(inst.extr.arg1.text, Seq(Interval.open(inst.extr.arg1.tokens.head.offsets.start, inst.extr.arg1.tokens.last.offsets.end))),
        arg2s = inst.extr.arg2s.map(arg2 => new Part(arg2.text, Seq(Interval.open(arg2.tokens.head.offsets.start, arg2.tokens.last.offsets.end)))),
        context = None,
        negated = false)
    }

    def convertRelnoun(inst: BinaryExtractionInstance[ChunkedToken]): Extraction = {
      new Extraction(
        rel = new Part(inst.extr.rel.text, Seq(inst.extr.rel.offsetInterval)),
        arg1 = new Part(inst.extr.arg1.text, Seq(inst.extr.arg1.offsetInterval)),
        arg2s = Seq(new Part(inst.extr.arg2.text, Seq(inst.extr.arg2.offsetInterval))),
        context = None,
        negated = false)
    }

    val extrs = (srlExtrs map convertSrl) // ++ (relnounExtrs map convertRelnoun)

    extrs
  }
}

