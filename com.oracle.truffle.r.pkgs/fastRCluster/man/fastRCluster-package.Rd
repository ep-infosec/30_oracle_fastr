% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/fastRCluster.R
\docType{package}
\name{fastRCluster-package}
\alias{fastRCluster}
\alias{fastRCluster-package}
\title{Run your R code faster with FastR!}
\description{
FastR is an alternative implementation of the R programming language,
which provides superb performance for computation intensive long
running jobs but takes bit more time to warm-up. The performance
of FastR is especially good with pure R code with loops. FastR aims to
be fully compatible with GNU-R including the R extensions API.
FastR is a part of GraalVM: multilingual virtual machine developed
by Oracle Labs.

Package \emph{fastRCluster} lets you run FastR inside GNU-R as a PSOCK cluster node.
With this package, you can move your performance critical R algorithms to FastR,
but keep the rest of your code-base on GNU-R. You can also use this package
to gradually move all your code to FastR.

We recommend using fastRCluster as a back-end for the \emph{future} package.
Keep your configuration of the \emph{future} package isolated from the rest
of the system to be able to simply switch between FastR and other back-ends.
}
\details{
This package does not come with pre-installed FastR. However, FastR can be
installed using the \code{installFastR} function. Once FastR is installed,
you can create PSOCK cluster nodes that delegate to FastR using \code{makeFastRCluster}.

If you already installed GraalVM and FastR on your system, set \code{graalvm.home}
R option or environment variable \code{GRAALVM_HOME} to the path pointing to
the installation directory of GraalVM.

You can use \code{fastRClusterInstallPackages} to install packages on the FastR engine.

FastR leverages dynamic just-in-time compilation. R functions are first interpreted
and then compiled. The first few executions are much slower. To re-use
the compiled code as much as possible, it is a good idea to first transfer all the
necessary R functions to the cluster nodes using \code{clusterExport} and then
reference those in the code snippets used in, .e.g., \code{clusterApply}.
If you send large and computation heavy R function via, e.g., \code{clusterApply},
it will be always deserialized to a different function on the other end in FastR and
the existing compiled code of the otherwise same function sent earlier will not be reused.
}
\examples{
library(fastRCluster)

\donttest{

# downloads and installs FastR, note: this may take a while
if (Sys.getenv("GRAALVM_HOME") != "") {
  installFastR()
}

# use the cluster package with FastR
fastrNode <- makeFastRCluster()
print(fastrNode)
# prints: FastR socket cluster with 1 nodes on host ???localhost???

# use the cluster package with FastR
# R.version will show that we are running that code on FastR
parallel::clusterApply(fastrNode, 'dummy', function(...) R.version)

}

\dontrun{

# install required packages on FastR
fastRClusterInstallPackages('ggplot2')

# use 'fastr': convenient wrapper around clusterApply
if (require(ggplot2)) {
  gg <- fastr(fastrNode, ggplot2::qplot(mpg, data=mtcars, geom="density",
    main=paste0("Generated by ", R.version$engine, "[", Sys.getpid(), "]")))
  plot(gg)
}

}

\donttest{

# transfer data and a helper function to the global environmnet of the cluster nodes
largeDataSet <- matrix(runif(1000000), 1000, 1000)
myComputation <- function(x) {
  x <- x/sum(x)
  res <- 0
  colsums <- colSums(x)
  rowsums <- rowSums(x)
  for(i in seq_along(1:nrow(x))){
    for(j in seq_along(1:ncol(x))){
      temp <- log((x[i,j]/(colsums[j]*rowsums[i])))
      res <- res + x[i,j] * if(is.finite(temp)) temp else 0
    }
  }
  res
}
parallel::clusterExport(fastrNode, c('largeDataSet', 'myComputation'))
# now you can refer to 'largeDataSet' and 'myComputation'
fastr(fastrNode, myComputation(largeDataSet))

# use the future package with FastR
if (require(future)) {
  future::plan(future::cluster, workers = fastrNode)
  val \%<-\% R.version
  print(val)
}

parallel::stopCluster(fastrNode) # stop FastR and close the connection

}
}
\seealso{
Useful links:
\itemize{
  \item \url{https://github.com/oracle/fastr/}
  \item Report bugs at \url{https://github.com/oracle/fastr/issues}
}

}
\keyword{internal}
