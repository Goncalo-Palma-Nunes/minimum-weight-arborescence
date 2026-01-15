package optimalarborescence.distance;

import java.io.Serializable;

import optimalarborescence.exception.NotImplementedException;

/*
 *  Asymmetric Hamming-like distance  function for
 *  all possible combinations of allelic values in the calculation of the edge distance
 *  d(u->v). The genetic distance for each locus within an ST profile is 0 when the locus
 *  contains the same allele in u and v, and 1 when that locus contains distinct alleles. It
 *  is 1 when v contains an existing allele at that locus but the allele is missing in u, and
 *  is otherwise 0
 *  <p>
 *  The direction is from u to v, where u has at most ( <= ) the same number of missing values than v.
 *  </p>
 * 
 *  Reference: Zhou Z, Alikhan NF, Sergeant MJ, Luhmann N, Vaz C, Francisco AP, Carriço JA, Achtman M. GrapeTree: visualization of core genomic relationships among 100,000 bacterial pathogens. Genome Res. 2018 Sep;28(9):1395-1404. doi: 10.1101/gr.232397.117. Epub 2018 Jul 26. PMID: 30049790; PMCID: PMC6120633.
*/
public class DirectionalHammingDistance implements DistanceFunction, Serializable  {

    private static final long serialVersionUID = 1L;

    @Override
    public double calculate(optimalarborescence.sequences.Sequence<?> seq1, optimalarborescence.sequences.Sequence<?> seq2) {
        if (seq1.getLength() != seq2.getLength()) {
            throw new IllegalArgumentException("Sequences must be of equal length");
        }
        if (seq1.getPositionsWithMissingData().size() > seq2.getPositionsWithMissingData().size()) {
            throw new IllegalArgumentException("First sequence must have at most the same number of missing data positions as the second sequence. Direction is from first to second sequence.");
        }
        
        int distance = 0;
        for (int i = 0; i < seq1.getLength(); i++) {

            // Asymmetric treatment of missing data
            if (seq2.isMissingDataAt(i) && !seq1.isMissingDataAt(i)) {
                distance++;
            } else if (!seq2.isMissingDataAt(i) && seq1.isMissingDataAt(i)) {
                // do nothing
            } else if (seq1.compareAt(i, seq2) > 0) {
                distance++;
            }
        }
        return distance;
    }

    @Override
    public String getDescription() {
        throw new NotImplementedException("getDescription is not implemented yet.");
    }
    
}
