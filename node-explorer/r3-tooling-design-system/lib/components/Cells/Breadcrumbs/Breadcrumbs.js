var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
var __rest = (this && this.__rest) || function (s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
};
import { jsx as _jsx } from "react/jsx-runtime";
var previewConfig = {
    name: 'breadcrumbs',
    defaultState: {
        componentProps: {},
    },
};
var Breadcrumbs = function (_a) {
    var _b = _a.className, className = _b === void 0 ? '' : _b, children = _a.children, otherProps = __rest(_a, ["className", "children"]);
    return (_jsx("div", __assign({ className: className + " text-xs uppercase" }, otherProps, { children: children }), void 0));
};
export default Breadcrumbs;
export { previewConfig };
//# sourceMappingURL=Breadcrumbs.js.map