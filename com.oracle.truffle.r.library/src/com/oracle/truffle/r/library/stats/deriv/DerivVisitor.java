/*
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2013,  The R Core Team
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.library.stats.deriv;

import static com.oracle.truffle.r.library.stats.deriv.Deriv.*;

import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxVisitor;

//Transcribed from GnuR, library/stats/src/deriv.c

public class DerivVisitor extends RSyntaxVisitor<RSyntaxElement> {

    private final String var;

    DerivVisitor(String var) {
        this.var = var;
    }

    @Override
    protected RSyntaxElement visit(RSyntaxCall call) {
        String functionName = getFunctionName(call);
        assert Utils.isInterned(functionName);

        RSyntaxElement arg0 = call.getSyntaxArguments()[0];
        RSyntaxElement arg1 = call.getSyntaxArguments().length > 1 ? call.getSyntaxArguments()[1] : null;

        if (Utils.identityEquals(functionName, LEFT_PAREN)) {
            return accept(arg0);
        }

        if (Utils.identityEquals(functionName, PLUS)) {
            if (call.getSyntaxArguments().length == 1) {
                return accept(arg0);
            } else {
                return simplify(PLUS, accept(arg0), accept(arg1));
            }
        }

        if (Utils.identityEquals(functionName, MINUS)) {
            if (call.getSyntaxArguments().length == 1) {
                return simplify(MINUS, accept(arg0), null);
            } else {
                return simplify(MINUS, accept(arg0), accept(arg1));
            }
        }

        if (Utils.identityEquals(functionName, TIMES)) {
            return simplify(PLUS, simplify(TIMES, accept(arg0), cloneElement(arg1)),
                            simplify(TIMES, cloneElement(arg0), accept(arg1)));
        }

        if (Utils.identityEquals(functionName, DIVIDE)) {
            return simplify(MINUS,
                            simplify(DIVIDE, accept(arg0), cloneElement(arg1)),
                            simplify(DIVIDE,
                                            simplify(TIMES, cloneElement(arg0), accept(arg1)),
                                            simplify(POWER, cloneElement(arg1), ConstantNode.create(2.))));
        }

        if (Utils.identityEquals(functionName, POWER)) {
            if (isNumeric(arg1)) {
                return simplify(TIMES,
                                arg1,
                                simplify(TIMES,
                                                accept(arg0),
                                                simplify(POWER, cloneElement(arg0), decDouble(arg1))));

            } else {
                // (a^b)' = a^(b-1).b.a' + a^b.log(a).b'
                RSyntaxElement expr1 = simplify(TIMES,
                                simplify(POWER,
                                                arg0,
                                                simplify(MINUS, cloneElement(arg1), ConstantNode.create(1.))),
                                simplify(TIMES, cloneElement(arg1), accept(arg0)));

                RSyntaxElement expr2 = simplify(TIMES,
                                simplify(POWER, cloneElement(arg0), cloneElement(arg1)),
                                simplify(TIMES,
                                                simplify(LOG, cloneElement(arg0), null),
                                                accept(arg1)));
                return simplify(PLUS, expr1, expr2);
            }
        }

        if (Utils.identityEquals(functionName, EXP)) {
            return simplify(TIMES, cloneElement(call), accept(arg0));
        }

        if (Utils.identityEquals(functionName, LOG)) {
            if (call.getSyntaxArguments().length != 1) {
                throw RError.error(RError.SHOW_CALLER, RError.Message.GENERIC, "only single-argument calls are supported");
            }
            return simplify(DIVIDE, accept(arg0), cloneElement(arg0));
        }

        if (Utils.identityEquals(functionName, COS)) {
            return simplify(TIMES,
                            simplify(SIN, cloneElement(arg0), null),
                            simplify(MINUS, accept(arg0), null));
        }

        if (Utils.identityEquals(functionName, SIN)) {
            return simplify(TIMES,
                            simplify(COS, cloneElement(arg0), null),
                            accept(arg0));
        }

        if (Utils.identityEquals(functionName, TAN)) {
            return simplify(DIVIDE,
                            accept(arg0),
                            simplify(POWER,
                                            simplify(COS, cloneElement(arg0), null),
                                            ConstantNode.create(2.)));
        }

        if (Utils.identityEquals(functionName, COSH)) {
            return simplify(TIMES,
                            simplify(SINH, cloneElement(arg0), null),
                            accept(arg0));
        }

        if (Utils.identityEquals(functionName, SINH)) {
            return simplify(TIMES,
                            simplify(COSH, cloneElement(arg0), null),
                            accept(arg0));
        }

        if (Utils.identityEquals(functionName, TANH)) {
            return simplify(DIVIDE,
                            accept(arg0),
                            simplify(POWER,
                                            simplify(COSH, cloneElement(arg0), null),
                                            ConstantNode.create(2.)));
        }

        if (Utils.identityEquals(functionName, SQRT)) {
            return accept(simplify(POWER, cloneElement(arg0), ConstantNode.create(0.5)));
        }

        if (Utils.identityEquals(functionName, PNORM)) {
            return simplify(TIMES,
                            simplify(DNORM, cloneElement(arg0), null),
                            accept(arg0));
        }

        if (Utils.identityEquals(functionName, DNORM)) {
            return simplify(TIMES,
                            simplify(MINUS, cloneElement(arg0), null),
                            simplify(TIMES,
                                            simplify(DNORM, cloneElement(arg0), null),
                                            accept(arg0)));
        }

        if (Utils.identityEquals(functionName, ASIN)) {
            return simplify(DIVIDE,
                            accept(arg0),
                            simplify(SQRT,
                                            simplify(MINUS,
                                                            ConstantNode.create(1.),
                                                            simplify(POWER, cloneElement(arg0), ConstantNode.create(2.))),
                                            null));
        }

        if (Utils.identityEquals(functionName, ACOS)) {
            return simplify(MINUS,
                            simplify(DIVIDE,
                                            accept(arg0),
                                            simplify(SQRT,
                                                            simplify(MINUS,
                                                                            ConstantNode.create(1.),
                                                                            simplify(POWER, cloneElement(arg0), ConstantNode.create(2.))),
                                                            null)),
                            null);
        }

        if (Utils.identityEquals(functionName, ATAN)) {
            return simplify(DIVIDE,
                            accept(arg0),
                            simplify(PLUS,
                                            ConstantNode.create(1.),
                                            simplify(POWER, cloneElement(arg0), ConstantNode.create(2.))));
        }

        if (Utils.identityEquals(functionName, LGAMMA)) {
            return simplify(TIMES,
                            accept(arg0),
                            simplify(DIGAMMA, cloneElement(arg0), null));
        }

        if (Utils.identityEquals(functionName, GAMMA)) {
            return simplify(TIMES,
                            accept(arg0),
                            simplify(TIMES, cloneElement(call),
                                            simplify(DIGAMMA, cloneElement(arg0), null)));
        }

        if (Utils.identityEquals(functionName, DIGAMMA)) {
            return simplify(TIMES,
                            accept(arg0),
                            simplify(TRIGAMMA, cloneElement(arg0), null));
        }

        if (Utils.identityEquals(functionName, TRIGAMMA)) {
            return simplify(TIMES,
                            accept(arg0),
                            simplify(PSIGAMMA, cloneElement(arg0), ConstantNode.create(2)));
        }

        if (Utils.identityEquals(functionName, PSIGAMMA)) {
            if (call.getSyntaxArguments().length == 1) {
                return simplify(TIMES,
                                accept(arg0),
                                simplify(PSIGAMMA, cloneElement(arg0), ConstantNode.create(1)));
            } else if (isIntegerOrDouble(arg1)) {
                return simplify(TIMES,
                                accept(arg0),
                                simplify(PSIGAMMA, cloneElement(arg0), incInteger(arg1)));
            } else {
                return simplify(TIMES,
                                accept(arg0),
                                simplify(PSIGAMMA,
                                                cloneElement(arg0),
                                                simplify(PLUS, cloneElement(arg1), ConstantNode.create(1))));
            }
        }

        throw RError.error(RError.SHOW_CALLER, RError.Message.NOT_IN_DERIVATIVE_TABLE, RDeparse.deparseSyntaxElement(call.getSyntaxLHS()));
    }

    @Override
    protected RSyntaxElement visit(RSyntaxConstant element) {
        return ConstantNode.create(0.);
    }

    @Override
    protected RSyntaxElement visit(RSyntaxLookup element) {
        double dVal = element.getIdentifier().equals(var) ? 1 : 0;
        return ConstantNode.create(dVal);
    }

    @Override
    protected RSyntaxElement visit(RSyntaxFunction element) {
        throw RInternalError.shouldNotReachHere();
    }

    private static RSyntaxElement simplify(String functionName, RSyntaxElement arg1, RSyntaxElement arg2) {
        if (Utils.identityEquals(functionName, PLUS)) {
            if (arg2 == null) {
                return arg1;
            } else if (isZero(arg1)) {
                return arg2;
            } else if (isZero(arg2)) {
                return arg1;
            } else if (isUminus(arg1)) {
                return simplify(MINUS, arg2, arg(arg1, 0));
            } else if (isUminus(arg2)) {
                return simplify(MINUS, arg1, arg(arg2, 0));
            } else {
                return newCall(PLUS, arg1, arg2);
            }
        } else if (Utils.identityEquals(functionName, MINUS)) {
            if (arg2 == null) {
                if (isZero(arg1)) {
                    return ConstantNode.create(0.);
                } else if (isUminus(arg1)) {
                    return arg(arg1, 0);
                } else {
                    return newCall(MINUS, arg1, arg2);
                }
            } else {
                if (isZero(arg2)) {
                    return arg1;
                } else if (isZero(arg1)) {
                    return simplify(MINUS, arg2, null);
                } else if (isUminus(arg1)) {
                    return simplify(MINUS,
                                    simplify(PLUS, arg(arg1, 0), arg2),
                                    null);
                } else if (isUminus(arg2)) {
                    return simplify(PLUS, arg1, arg(arg2, 0));
                } else {
                    return newCall(MINUS, arg1, arg2);
                }
            }
        } else if (Utils.identityEquals(functionName, TIMES)) {
            if (isZero(arg1) || isZero(arg2)) {
                return ConstantNode.create(0.);
            } else if (isOne(arg1)) {
                return arg2;
            } else if (isOne(arg2)) {
                return arg1;
            } else if (isUminus(arg1)) {
                return simplify(MINUS, simplify(TIMES, arg(arg1, 0), arg2), null);
            } else if (isUminus(arg2)) {
                return simplify(MINUS, simplify(TIMES, arg1, arg(arg2, 0)), null);
            } else {
                return newCall(TIMES, arg1, arg2);
            }
        } else if (Utils.identityEquals(functionName, DIVIDE)) {
            if (isZero(arg1)) {
                return ConstantNode.create(0.);
            } else if (isZero(arg2)) {
                return ConstantNode.create(RRuntime.DOUBLE_NA);
            } else if (isOne(arg2)) {
                return arg1;
            } else if (isUminus(arg1)) {
                return simplify(MINUS, simplify(DIVIDE, arg(arg1, 0), arg2), null);
            } else if (isUminus(arg2)) {
                return simplify(MINUS, simplify(DIVIDE, arg1, arg(arg2, 0)), null);
            } else {
                return newCall(DIVIDE, arg1, arg2);
            }
        } else if (Utils.identityEquals(functionName, POWER)) {
            if (isZero(arg2)) {
                return ConstantNode.create(1.);
            } else if (isZero(arg1)) {
                return ConstantNode.create(0.);
            } else if (isOne(arg1)) {
                return ConstantNode.create(1.);
            } else if (isOne(arg2)) {
                return arg1;
            } else {
                return newCall(POWER, arg1, arg2);
            }
        } else if (Utils.identityEquals(functionName, EXP)) {
            // FIXME: simplify exp(lgamma( E )) = gamma( E )
            return newCall(EXP, arg1, null);
        } else if (Utils.identityEquals(functionName, LOG)) {
            // FIXME: simplify log(gamma( E )) = lgamma( E )
            return newCall(LOG, arg1, null);
        } else if (Utils.identityEquals(functionName, COS) || Utils.identityEquals(functionName, SIN) || Utils.identityEquals(functionName, TAN) || Utils.identityEquals(functionName, COSH) ||
                        Utils.identityEquals(functionName, SINH) || Utils.identityEquals(functionName, TANH) || Utils.identityEquals(functionName, SQRT) ||
                        Utils.identityEquals(functionName, PNORM) || Utils.identityEquals(functionName, DNORM) || Utils.identityEquals(functionName, ASIN) ||
                        Utils.identityEquals(functionName, ACOS) || Utils.identityEquals(functionName, ATAN) || Utils.identityEquals(functionName, GAMMA) ||
                        Utils.identityEquals(functionName, LGAMMA) ||
                        Utils.identityEquals(functionName, DIGAMMA) || Utils.identityEquals(functionName, TRIGAMMA) || Utils.identityEquals(functionName, PSIGAMMA)) {
            return newCall(functionName, arg1, arg2);
        } else {
            return ConstantNode.create(RRuntime.DOUBLE_NA);
        }
    }

    private static boolean isIntegerOrDouble(RSyntaxElement elem) {
        if (elem instanceof RSyntaxConstant) {
            Object val = ((RSyntaxConstant) elem).getValue();
            return val instanceof Integer || val instanceof Double;
        } else {
            return false;
        }
    }

    private static boolean isUminus(RSyntaxElement elem) {
        if (elem instanceof RSyntaxCall && Utils.identityEquals(MINUS, getFunctionName(elem))) {
            RSyntaxElement[] args = ((RSyntaxCall) elem).getSyntaxArguments();
            switch (args.length) {
                case 1:
                    return true;
                case 2:
                    return false;
                default:
                    throw RError.error(RError.SHOW_CALLER, RError.Message.GENERIC, "invalid form in unary minus check");
            }
        } else {
            return false;
        }
    }

    private static boolean isNumeric(RSyntaxElement elem) {
        if (elem instanceof RSyntaxConstant) {
            Object val = ((RSyntaxConstant) elem).getValue();
            return val instanceof Integer || val instanceof Double || val instanceof Byte;
        } else {
            return false;
        }
    }

    private static RSyntaxConstant decDouble(RSyntaxElement elem) {
        assert elem instanceof RSyntaxConstant;
        assert ((RSyntaxConstant) elem).getValue() instanceof Number;
        Number n = (Number) ((RSyntaxConstant) elem).getValue();
        return ConstantNode.create(n.doubleValue() - 1);
    }

    private static RSyntaxConstant incInteger(RSyntaxElement elem) {
        assert elem instanceof RSyntaxConstant;
        assert ((RSyntaxConstant) elem).getValue() instanceof Number;
        Number n = (Number) ((RSyntaxConstant) elem).getValue();
        return ConstantNode.create(n.intValue() + 1);
    }
}
